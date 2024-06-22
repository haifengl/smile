import { useState } from 'react'
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
          // Get the reader from the stream
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
                // Convert the chunk value to a string
                const chunk = decoder.decode(value);
                for (let link of chunk.split('data:')) {
                  message.text += link;
                }
                setMessages([...history, message]);
                // Read the next chunk
                readChunk();
              })
              .catch(error => {
                console.error(error);
                throw error;
              });
          };
          // Start reading the chunks
          readChunk();
        })
        .catch(error => {
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
      <Chat style={{ height: '90vh', width: '600px', border: 'none', margin: '0', padding: '0' }}
          userId={user.id}
          messages={messages}
          onSendMessage={sendMessage}
          showTypingIndicator={showTypingIndicator}
          placeholder="Type prompt here"
      />
  )
}

export default App
