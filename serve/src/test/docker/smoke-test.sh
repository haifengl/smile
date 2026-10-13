#!/usr/bin/env bash
#
# Smoke test for a built smile-serve container image (any of the published flavors).
#
#   serve/src/test/docker/smoke-test.sh IMAGE [--flavor full|micro] [--port N] [--timeout SECONDS]
#                                             [--chat-model SPEC] [--hf-home DIR] [--keep]
#
# Starts IMAGE with a few tiny models (a random forest, LDA, and a logistic-regression ONNX model), waits for it to
# become healthy and checks the API:
#   all flavors  health, Prometheus metrics, web UI, model list, SMILE prediction with SHAP, LDA prediction (native
#                BLAS/LAPACK), ONNX inference
#   full         (default) also the auth/user and conversation endpoints (database), and, with --chat-model, chat
#                completions, streaming and not (--hf-home is mounted as the Hugging Face cache)
#   micro        chat and auth endpoints must NOT exist (404)
#
# Examples:
#   serve/src/test/docker/smoke-test.sh quarkus/smile-serve-native
#   serve/src/test/docker/smoke-test.sh quarkus/smile-serve-native-micro --flavor micro
#   serve/src/test/docker/smoke-test.sh quarkus/smile-serve-native --chat-model microsoft/Phi-4-mini-instruct-onnx \
#       --hf-home /path/to/huggingface
#
# Needs docker, curl and python3 on the host. Exit status is 0 only if every check passes.
set -u

FLAVOR=full
PORT=18080
TIMEOUT=120
CHAT_MODEL=
HF_HOME_DIR=
KEEP=0
IMAGE=${1:-}
if [ -z "$IMAGE" ] || [ "${IMAGE#-}" != "$IMAGE" ]; then
    sed -n '3,22p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
fi
shift
while [ $# -gt 0 ]; do
    case "$1" in
        --flavor) FLAVOR=$2; shift 2 ;;
        --port) PORT=$2; shift 2 ;;
        --timeout) TIMEOUT=$2; shift 2 ;;
        --chat-model) CHAT_MODEL=$2; shift 2 ;;
        --hf-home) HF_HOME_DIR=$2; shift 2 ;;
        --keep) KEEP=1; shift ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done
case "$FLAVOR" in full|micro) ;; *) echo "--flavor must be full or micro" >&2; exit 2 ;; esac

HERE=$(cd "$(dirname "$0")" && pwd)
NAME="smile-smoke-$$"
WORK=$(mktemp -d)
BASE="http://localhost:$PORT"
API="$BASE/api/v1"
PASS=0
FAIL=0

cleanup() {
    if [ "$KEEP" = 1 ]; then
        echo "kept container $NAME (port $PORT) and models in $WORK"
    else
        docker rm -f "$NAME" >/dev/null 2>&1
        rm -rf "$WORK" 2>/dev/null || docker run --rm -v "$WORK":/w --entrypoint sh "$IMAGE" -c 'rm -rf /w/*' >/dev/null 2>&1
        rmdir "$WORK" 2>/dev/null
    fi
}
trap cleanup EXIT

ok()   { PASS=$((PASS + 1)); printf '  PASS  %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  FAIL  %s%s\n' "$1" "${2:+  ($2)}"; }

# http_status NAME EXPECTED_STATUS curl-args...
http_status() {
    local name=$1 expect=$2; shift 2
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' "$@")
    if [ "$code" = "$expect" ]; then ok "$name"; else bad "$name" "HTTP $code, expected $expect"; fi
}

# json_check NAME PYTHON_EXPR curl-args...   (the response is available as `d`; the expression must be truthy)
json_check() {
    local name=$1 expr=$2; shift 2
    local body
    body=$(curl -s --max-time 120 "$@")
    if printf '%s' "$body" | python3 -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if ($expr) else 1)" 2>/dev/null; then
        ok "$name"
    else
        bad "$name" "unexpected response: $(printf '%s' "$body" | head -c 160)"
    fi
}

SAMPLE='{"sepallength":6.7,"sepalwidth":3.0,"petallength":5.2,"petalwidth":2.3}'
JSON='Content-Type: application/json'

# --- models: reuse the unit-test random forest, plus an LDA model and a tiny ONNX model
cp "$HERE/../resources/model/iris_random_forest.sml" "$HERE/models/iris_lda.sml" "$HERE/models/logreg_iris.onnx" "$WORK/"
chmod -R a+rwX "$WORK"

echo "== $IMAGE ($FLAVOR flavor) on port $PORT"
RUN_ARGS=(-d --name "$NAME" -u root -p "$PORT:8080" -v "$WORK":/model)
[ -n "$HF_HOME_DIR" ] && RUN_ARGS+=(-v "$HF_HOME_DIR":/model/huggingface:ro)
[ -n "$CHAT_MODEL" ] && RUN_ARGS+=(-e "SMILE_CHAT_MODEL=$CHAT_MODEL")
if ! docker run "${RUN_ARGS[@]}" "$IMAGE" >/dev/null; then echo "could not start the container" >&2; exit 1; fi

STARTED=0
for _ in $(seq 1 "$TIMEOUT"); do
    if curl -sf "$BASE/q/health" >/dev/null 2>&1; then STARTED=1; break; fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != true ]; then break; fi
    sleep 1
done
if [ "$STARTED" != 1 ]; then
    echo "the service did not become healthy within ${TIMEOUT}s; container log:" >&2
    docker logs "$NAME" 2>&1 | tail -40 >&2
    exit 1
fi
[ -n "$CHAT_MODEL" ] && sleep 5   # chat models load asynchronously after the HTTP port opens
printf '  startup: %s\n' "$(docker logs "$NAME" 2>&1 | grep -o 'started in [0-9.]*s' | head -1)"

# --- checks common to every flavor
http_status "health" 200 "$BASE/q/health"
if curl -s "$BASE/q/metrics" | grep -q '^# HELP'; then ok "prometheus metrics (/q/metrics)"; else bad "prometheus metrics (/q/metrics)"; fi
http_status "web UI" 200 "$BASE/"
json_check  "model list has the SMILE and ONNX models" \
    "{'iris_random_forest-1','iris_lda-1','logreg_iris'} <= {m['id'] for m in d['data']}" "$API/models"
json_check  "random forest prediction with SHAP" \
    "d['prediction']==2 and len(d['probabilities'])==3 and d['explanations']['shap']" \
    -X POST -H "$JSON" -d "$SAMPLE" "$API/smile/iris_random_forest-1?explain=true"
json_check  "LDA prediction (native BLAS/LAPACK)" \
    "d['prediction']==2 and abs(sum(d['probabilities'])-1)<0.01" \
    -X POST -H "$JSON" -d "$SAMPLE" "$API/smile/iris_lda-1"
json_check  "ONNX model info" "d['id']=='logreg_iris' and d['inputs'][0]['name']=='float_input'" "$API/onnx/logreg_iris"
json_check  "ONNX inference (ONNX Runtime)" "len(d['label'])==3" \
    -X POST -H "$JSON" -d '{"float_input":[1,2,3,4,5,6]}' "$API/onnx/logreg_iris"

# --- flavor specific
if [ "$FLAVOR" = full ]; then
    http_status "user endpoint (auth + database)" 200 "$API/users/me"
    http_status "conversation endpoint (database)" 200 "$API/conversations"
    if [ -n "$CHAT_MODEL" ]; then
        MODEL_ID=$(curl -s "$API/models" | python3 -c "import sys,json; print(next((m['id'] for m in json.load(sys.stdin)['data'] if m['kind']=='LLM'), ''))")
        if [ -z "$MODEL_ID" ]; then
            bad "chat model loaded" "no LLM in /models; chat model spec: $CHAT_MODEL"
        else
            REQ="{\"model\":\"$MODEL_ID\",\"messages\":[{\"role\":\"user\",\"content\":\"What is the capital of France? Answer in one short sentence.\"}],\"max_tokens\":40,\"temperature\":0"
            json_check "chat completion" "'Paris' in d['choices'][0]['message']['content']" \
                -X POST -H "$JSON" -d "$REQ}" "$API/chat/completions"
            CHUNKS=$(curl -sN --max-time 120 -X POST -H "$JSON" -d "$REQ,\"stream\":true}" "$API/chat/completions")
            if printf '%s' "$CHUNKS" | grep -q '"content":"' && printf '%s' "$CHUNKS" | grep -q '^data:\[DONE\]'; then
                ok "chat completion streaming"
            else
                bad "chat completion streaming" "$(printf '%s' "$CHUNKS" | head -c 160)"
            fi
        fi
    fi
else
    http_status "no user endpoint (404)" 404 "$API/users/me"
    http_status "no conversation endpoint (404)" 404 "$API/conversations"
    http_status "no chat endpoint (404)" 404 -X POST -H "$JSON" -d '{"model":"x","messages":[]}' "$API/chat/completions"
    json_check  "model list has no LLM" "all(m['kind']!='LLM' for m in d['data'])" "$API/models"
fi

# --- the log must be free of errors
ERRORS=$(docker logs "$NAME" 2>&1 | grep -E ' ERROR ' | head -3)
if [ -z "$ERRORS" ]; then ok "no ERROR lines in the container log"; else bad "no ERROR lines in the container log" "$ERRORS"; fi

echo "== $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
