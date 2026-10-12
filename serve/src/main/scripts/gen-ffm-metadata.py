#!/usr/bin/env python3
"""Generates the GraalVM native-image reachability metadata for serve (FFM + Java serialization).

Native images must know every FFM downcall/upcall signature at build time. The jextract-generated
bindings (BLAS/LAPACK/ARPACK, ONNX Runtime, ORT GenAI, libsmile_torch) only use primitive and
pointer layouts, so the set of signatures is derived by scanning FunctionDescriptor.of/ofVoid calls.
Classic ML models (.sml) are Java-serialized object graphs; every Serializable class in base/core
plus the JDK types below must be registered or ObjectInputStream fails with InvalidClassException.
Re-run after regenerating any binding or adding/removing Serializable model classes
(needs compiled classes: ./gradlew :core:classes):

    python3 serve/src/main/scripts/gen-ffm-metadata.py
"""
import json, os, re, subprocess, sys, tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../../..'))
OUT = os.path.join(ROOT, 'serve/src/main/resources/META-INF/native-image/smile/serve/reachability-metadata.json')

# x86_64 / aarch64 Linux LP64 layouts.
LEAF = {
    'C_POINTER': 'void*', 'ADDRESS': 'void*',
    'C_INT': 'jint', 'JAVA_INT': 'jint',
    'C_LONG': 'jlong', 'C_LONG_LONG': 'jlong', 'JAVA_LONG': 'jlong',
    'C_DOUBLE': 'jdouble', 'JAVA_DOUBLE': 'jdouble',
    'C_FLOAT': 'jfloat', 'JAVA_FLOAT': 'jfloat',
    'C_BOOL': 'jboolean', 'C_CHAR': 'jbyte', 'C_SHORT': 'jshort',
}

def args_of(s, i):
    d, j = 1, i
    while d and j < len(s):
        d += (s[j] == '(') - (s[j] == ')')
        j += 1
    return s[i:j - 1]

sigs = set()      # downcall signatures with their critical option
all_sigs = set()  # plain signatures (upcalls)
fn_types = set()   # callback interfaces resolved reflectively by jextract's upcallHandle(X.class, "apply", ...)
unresolved = []

def binary_name(path, text, ref):
    """Binary name of the interface 'ref' (e.g. GetApi.Function) referenced inside the top-level class of 'path'."""
    pkg = re.search(r'^package\s+([\w.]+);', text, re.M).group(1)
    top = os.path.splitext(os.path.basename(path))[0]
    segs = ref.split('.')
    if segs[0] == top:
        segs = segs[1:]
    return pkg + '.' + '$'.join([top] + segs)

def class_exists(path, name):
    # <module>/src/main/java/... -> <module>/build/classes/java/main/...
    module = path.split('/src/main/java/')[0]
    rel = name.replace('.', '/') + '.class'
    return os.path.exists(os.path.join(module, 'build/classes/java/main', rel))

for base, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in ('node_modules', 'build', '.git', '.gradle', 'test')]
    for f in files:
        if not f.endswith('.java'):
            continue
        path = os.path.join(base, f)
        s = open(path, errors='ignore').read()
        for m in re.finditer(r'upcallHandle\(\s*([\w.]+)\.class', s):
            name = binary_name(path, s, m.group(1))
            if os.path.isdir(os.path.join(path.split('/src/main/java/')[0], 'build/classes')) and not class_exists(path, name):
                unresolved.append((f, name))
            fn_types.add(name)
        matches = list(re.finditer(r'FunctionDescriptor\.(of|ofVoid)\(', s))
        for i, m in enumerate(matches):
            parts = [p.strip() for p in re.split(r',\s*(?![^()]*\))', args_of(s, m.end())) if p.strip()]
            try:
                leaves = [LEAF[re.split(r'[.]', p)[-1]] for p in parts]
            except KeyError as e:
                sys.exit(f'{f}: unsupported layout {e} in {parts}')
            if m.group(1) == 'of':
                ret, params = leaves[0], leaves[1:]
            else:
                ret, params = 'void', leaves
            # jextract declares the descriptor and its downcallHandle(...) side by side; the Linker option
            # (critical(true) = heap segments allowed, passed as Object+offset) changes the registered leaf type.
            seg = s[m.end():matches[i + 1].start() if i + 1 < len(matches) else len(s)]
            h = re.search(r'downcallHandle\(([^;]*)\);', seg)
            opt = None
            if h:
                c = re.search(r'Linker\.Option\.critical\((true|false)\)', h.group(1))
                if c:
                    opt = 'heap' if c.group(1) == 'true' else 'critical'
                if 'firstVariadicArg' in h.group(1) or 'captureCallState' in h.group(1):
                    sys.exit(f'{f}: unsupported Linker option in {h.group(1)}')
            sigs.add((ret, tuple(params), opt))
            all_sigs.add((ret, tuple(params)))

OPTS = {'heap': {'critical': {'allowHeapAccess': True}}, 'critical': {'critical': True}}
down = [dict({'returnType': r, 'parameterTypes': list(p)}, **({'options': OPTS[o]} if o else {}))
        for r, p, o in sorted(sigs, key=lambda x: (x[0], x[1], x[2] or ''))]
entries = [{'returnType': r, 'parameterTypes': list(p)} for r, p in sorted(all_sigs)]
# ---- Java serialization (.sml models) ----
JDK_SERIALIZABLE = [
    'java.lang.String', 'java.lang.Number', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Double',
    'java.lang.Float', 'java.lang.Short', 'java.lang.Byte', 'java.lang.Boolean', 'java.lang.Character',
    'java.lang.Enum', 'java.lang.Object', 'java.math.BigDecimal', 'java.math.BigInteger',
    'java.util.ArrayList', 'java.util.LinkedList', 'java.util.ArrayDeque', 'java.util.Vector',
    'java.util.HashMap', 'java.util.LinkedHashMap', 'java.util.TreeMap', 'java.util.Hashtable',
    'java.util.Properties', 'java.util.HashSet', 'java.util.LinkedHashSet', 'java.util.TreeSet',
    'java.util.BitSet', 'java.util.EnumMap', 'java.util.Locale', 'java.util.Date', 'java.util.UUID',
    'java.util.Random', 'java.util.concurrent.ConcurrentHashMap', 'java.util.concurrent.atomic.AtomicInteger',
    'java.util.concurrent.atomic.AtomicLong', 'java.util.concurrent.atomic.AtomicReference',
    'java.util.Collections$EmptyList', 'java.util.Collections$EmptyMap', 'java.util.Collections$EmptySet',
    'java.util.Collections$SingletonList', 'java.util.Collections$SingletonMap', 'java.util.Collections$SingletonSet',
    'java.util.Collections$UnmodifiableCollection', 'java.util.Collections$UnmodifiableList',
    'java.util.Collections$UnmodifiableRandomAccessList', 'java.util.Collections$UnmodifiableMap',
    'java.util.Collections$UnmodifiableSet', 'java.util.Collections$SynchronizedList',
    'java.util.Collections$SynchronizedMap', 'java.util.Arrays$ArrayList', 'java.util.CollSer',
    'java.util.ImmutableCollections$ListN', 'java.util.ImmutableCollections$List12',
    'java.util.ImmutableCollections$SetN', 'java.util.ImmutableCollections$Set12',
    'java.util.ImmutableCollections$MapN', 'java.util.ImmutableCollections$Map1',
    'java.time.Ser', 'java.time.LocalDate', 'java.time.LocalTime', 'java.time.LocalDateTime',
    'java.time.Instant', 'java.time.Duration', 'java.time.ZoneId', 'java.time.ZoneOffset',
    'java.time.ZonedDateTime', 'java.time.OffsetDateTime', 'java.time.Period',
    'java.sql.Timestamp', 'java.sql.Date', 'java.sql.Time',
]
PRIMITIVE_ARRAYS = ['double[]', 'int[]', 'long[]', 'float[]', 'boolean[]', 'byte[]', 'char[]', 'short[]',
                    'double[][]', 'int[][]', 'java.lang.String[]', 'java.lang.Object[]', 'java.lang.Integer[]',
                    'java.lang.Double[]']

def scan_serializable():
    init = tempfile.NamedTemporaryFile('w', suffix='.gradle', delete=False)
    init.write('allprojects { if (path == ":core") { tasks.register("printCp") { doLast { '
               'println "CP=" + project.sourceSets.main.runtimeClasspath.asPath } } } }')
    init.close()
    try:
        out = subprocess.run([os.path.join(ROOT, 'gradlew'), '-q', '--no-configuration-cache', '-I', init.name,
                              ':core:classes', ':core:printCp'], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    finally:
        os.unlink(init.name)
    cp = next(l[3:] for l in out.splitlines() if l.startswith('CP='))
    dirs = [os.path.join(ROOT, m, 'build/classes/java/main') for m in ('base', 'core')]
    res = subprocess.run(['java', os.path.join(ROOT, 'serve/src/main/scripts/ListSerializable.java'), cp] + dirs,
                         cwd=ROOT, capture_output=True, text=True, check=True)
    return res.stdout.split()

project_ser = scan_serializable()
ser_types = sorted(set(project_ser) | set(JDK_SERIALIZABLE))
ser_types += PRIMITIVE_ARRAYS + [t + '[]' for t in project_ser]

# ---- Jackson-bound classes in core (not Jandex-indexed, so Quarkus does not register them) ----
json_types = []
core_src = os.path.join(ROOT, 'core/src/main/java')
core_cls = os.path.join(ROOT, 'core/build/classes/java/main')
for base, dirs, files in os.walk(core_src):
    for f in files:
        if not f.endswith('.java'):
            continue
        text = open(os.path.join(base, f), errors='ignore').read()
        if not re.search(r'import (com\.fasterxml|tools)\.jackson', text):
            continue
        rel = os.path.relpath(os.path.join(base, f[:-5]), core_src)
        cdir = os.path.join(core_cls, os.path.dirname(rel))
        top = os.path.basename(rel)
        for c in sorted(os.listdir(cdir)) if os.path.isdir(cdir) else []:
            if c == top + '.class' or (c.startswith(top + '$') and c.endswith('.class')):
                json_types.append(os.path.join(os.path.dirname(rel), c[:-6]).replace('/', '.'))

if unresolved:
    sys.exit('callback interfaces not found in build/classes (run ./gradlew compileJava): ' + repr(unresolved[:5]))
meta = {'reflection': [{'type': t, 'allDeclaredMethods': True} for t in sorted(fn_types)]
                      + [{'type': t, 'allDeclaredConstructors': True, 'allPublicConstructors': True,
                          'allDeclaredMethods': True, 'allPublicMethods': True,
                          'allDeclaredFields': True, 'allPublicFields': True} for t in sorted(json_types)],
        'serialization': [{'type': t} for t in ser_types],
        'foreign': {'downcalls': down, 'upcalls': entries}}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w') as fh:
    json.dump(meta, fh, indent=1)
    fh.write('\n')
print(f'{len(json_types)} JSON types, {len(ser_types)} serialization types, {len(fn_types)} callback interfaces, {len(down)} downcall / {len(entries)} upcall signatures -> {os.path.relpath(OUT, ROOT)}')
