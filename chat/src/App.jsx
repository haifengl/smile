import { useEffect, useRef, useState } from 'react'
import { fetchEventSource, EventStreamContentType } from '@microsoft/fetch-event-source';
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
    name: 'Llama3',
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
  const [threadId, setThreadId] = useState(0);

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
          setThreadId(thread.id);
        })
        .catch(error => {
          console.error(error);
        });
    }
  }, [])

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
          "role": "user",
          "content": text
        }
      ]
    };

    // Guide the system if this is the first user message.
    if (messages.length == 2) {
      data.messages.unshift({
          "role": "system",
          "content": "You are a helpful, respectful and honest assistant."
      });
    }

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

      const history = messages;
      const message = {
        text: '',
        user: bot,
        createdAt: new Date(),
      };

      fetchEventSource(url, {
        ...requestOptions,
        async onopen(response) {
          if (response.ok && response.headers.get('content-type') === EventStreamContentType) {
            return; // everything's good
          } else {
            throw new Error(response.statusText);
          }
        },
        onmessage(msg) {
          // if the server emits an error message, throw an exception
          // so it gets handled by the onerror callback below:
          if (msg.event === 'FatalError') {
            throw new Error(msg.data);
          }

          message.text += msg.data;
          setMessages([...history, message]);
        },
        onclose() {
          console.log("Server closes the connection");
          setShowTypingIndicator(false);
        },
        onerror(error) {
          console.error(error);
          messages.push({
            text: error.message,
            user: server,
            createdAt: new Date(),
          });

          setMessages([...messages]);
          setShowTypingIndicator(false);
          throw error; // rethrow to stop the operation
        }
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
