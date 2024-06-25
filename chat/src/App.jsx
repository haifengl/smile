import { useEffect, useState } from 'react'
import Chat from './chat/Chat'
import InternetIcon from './assets/internet.svg'
import './App.css'

function App() {
  const user = {
    id: 'user',
    name: 'You',
  };

  const bot = {
    id: 'meta/llama3',
    name: 'Copilot',
    avatar: 'https://haifengl.github.io/images/smile.jpg',
  };
  
  const server = {
    id: 'server',
    name: 'Server',
    avatar: InternetIcon,
  };

  const [messages, setMessages] = useState([
    {
      text: 'Hello! How are you today? As a helpful, respectful and honest assistant, I am happy to serve you.',
      user: bot,
    },
  ]);

  const [showTypingIndicator, setShowTypingIndicator] = useState(false);

  // Conversation thread ID
  let threadId = 0;
  useEffect(() => {
    if (threadId <= 0) {
      const requestOptions = {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({}),
      };

      fetch('/v1/threads', requestOptions)
        .then(response => {
          if (!response.ok) {
            throw new Error(response.statusText);
          }
          return response.json();
        })
        .then(thread => {
          console.log(thread);
          threadId = thread.id;
        })
        .catch(error => {
          console.error(error);
        });
    }
    getAns();
  }, [])

  /** Computes the longest proper prefix which is also a suffix as in KMP algorithm. */
  const computeLPS = function(word) {
    word = word.split('');
    let results = [];
    let pos = 2;
    let cnd = 0;

    results[0] = -1;
    results[1] = 0;
    while (pos < word.length) {
        if (word[pos - 1] == word[cnd]) {
            cnd++;
            results[pos] = cnd;
            pos++;
        } else if (cnd > 0) {
            cnd = results[cnd];
        } else {
            results[pos] = 0;
            pos++;
        }
    }
    return results;
  };

  const lps1 = computeLPS('\ndata:');
  const lps2 = computeLPS('\n\ndata:');

  const replaceAll = function(array, word, lps, replacement) {
    let index = -1;
    let m = 0;
    let i = 0;

    while (m + i < array.length) {
        if (word[i] == array[m + i]) {
            if (i == word.length - 1) {
                return m;
            }
            i++;
        } else {
            m = m + i - lps[i];
            if (lps[i] > -1) {
                i = lps[i];
            } else {
                i = 0;
            }
        }
    }
    return index;
  };

  const sendMessage = (text) => {
    messages.push({
      user: user,
      text: text,
      createdAt: new Date(),
    });

    setMessages([...messages]);
    setShowTypingIndicator(true);

    const data = {
      "model": "meta/llama3",
      "threadId": threadId,
      "stream": true,
      "messages": [
        {
          "role": "system",
          "content": "You are a helpful, respectful and honest assistant."
        },
        {
          "role": "user",
          "content": text
        }
      ]
    };

    const url = '/v1/chat/completions';
    const requestOptions = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    };

    if (data["stream"]) {
      requestOptions['headers']['Accept'] = 'text/event-stream';
      fetch(url, requestOptions)
        .then(response => {
          let offset = 0;
          const buffer = new Uint8Array(327680); // 32K tokens x 10 chars
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          const history = messages;
          const message = {
            text: '',
            user: bot,
            createdAt: new Date(),
          };

          // Recursive function to read each chunk
          const readChunk = () => {
            // Read a chunk from the reader
            reader.read()
              .then(({value, done}) => {
                if (done) {
                  if (message.text === '') {
                    message.user = server;
                    message.text = 'Empty response. Probably bad request.';
                    setMessages([...history, message]);
                  }
                  setShowTypingIndicator(false);
                  return;
                }

                // Always parse from beginning.
                // The format of a chunk may be ill-formed
                // due to the streaming chunk breaks and
                // SSE's newline breaks between events.
                buffer.set(value, offset);
                offset += value.length;
                let content = decoder.decode(buffer.subarray(0, offset));
                // strip first data:
                content = content.substring(5);
                // remove \n\n between events
                content = content.replaceAll('\n\ndata:', '');
                // process multiline event
                message.text = content.replaceAll('\ndata:', '\n');
                setMessages([...history, message]);
                // Read the next chunk
                readChunk();
              })
              .catch(error => {
                console.error(error);
                messages.push({
                  text: error.message,
                  user: server,
                  createdAt: new Date(),
                });

                setMessages([...messages]);
                setShowTypingIndicator(false);
              });
          };
          // Start reading the chunks
          readChunk();
        })
        .catch(error => {
          console.error(error);
          messages.push({
            text: error.message,
            user: server,
            createdAt: new Date(),
          });

          setMessages([...messages]);
          setShowTypingIndicator(false);
        });
    } else {
      fetch(url, requestOptions)
        .then(response => {
          if (!response.ok) {
            throw new Error(response.statusText);
          }
          return response.json();
        })
        .then(data => {
          console.log(data);

          let content = data['choices'][0]['message']['content'];
          content = content.trim();

          messages.push({
            text: content,
            user: bot,
            createdAt: new Date(data['created']),
          });

          setMessages([...messages]);
          setShowTypingIndicator(false);
        })
        .catch(error => {
          console.error(error);
          messages.push({
            text: error.message,
            user: server,
            createdAt: new Date(),
          });

          setMessages([...messages]);
          setShowTypingIndicator(false);
        });
    }
  }

  return (
      <Chat
          userId={user.id}
          messages={messages}
          onSendMessage={sendMessage}
          showTypingIndicator={showTypingIndicator}
          placeholder="Type prompt here"
      />
  )
}

export default App
