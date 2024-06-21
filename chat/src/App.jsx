import { useState } from 'react'
import Chat from './chat/Chat'
import './App.css'

function App() {
  const user = {
    id: 'user',
    name: 'You',
  };

  const bot = {
    id: 'meta/llama3',
    name: 'Copilot',
    avatar: 'http://haifengl.github.io/images/smile.jpg',
  };
  
  const server = {
    id: 'server',
    name: 'Server',
  };

  const system = {
    role: "system",
    content: "You are a helpful, respectful and honest assistant."
  };

  const textMessage = (text) => {
    return `<span style="white-space: pre-wrap">${text}</span>`;
  }

  const [messages, setMessages] = useState([
    {
      text: textMessage('Hello! How are you today? As a helpful, respectful and honest assistant, I am happy to serve you.'),
      user: bot,
    },
  ]);

  const [showTypingIndicator, setShowTypingIndicator] = useState(false);

  const sendMessage = (text) => {
    messages.push({
      text: textMessage(text),
      user: user,
      createdAt: new Date(),
    });

    setMessages([...messages]);
    setShowTypingIndicator(true);

    const data = {
      "model": "meta/llama3",
      "stream": true,
      "messages": [
        system,
        {
          "role": "user",
          "content": text
        }
      ]
    };

    const requestOptions = {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    };

    fetch('/v1/chat/completions', requestOptions)
      .then(response => {
        if (!response.ok) {
          throw new Error(response.statusText);
        }
        return response.json();
      })
      .then(data => {
        console.log(data);

        let response = data['outputs'][0]['data'][0];
        response = response.trim();

        messages.push({
          text: textMessage(response),
          user: bot,
          createdAt: new Date(),
        });

        setMessages([...messages]);
        setShowTypingIndicator(false);
      })
      .catch(error => {
        messages.push({
          text: textMessage(error.message),
          user: server,
          createdAt: new Date(),
        });

        setMessages([...messages]);
        setShowTypingIndicator(false);
      });
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
