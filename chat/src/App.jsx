/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
import { useEffect, useState } from 'react'
import { SSE } from 'sse.js'
import Chat from './chat/Chat'
import InternetIcon from './assets/internet.svg'
import LlamaIcon from './assets/llama.svg'
import './App.css'

function App() {
  const user = {
    id: 'user',
    name: 'You',
  };

  const bot = {
    id: 'smile',
    name: 'Kirin',
    avatar: LlamaIcon,
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
      "model": "deepseek-r1:70b",
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
    if (messages.length === 2) {
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
        'Cache-Control': 'no-cache'
      },
    };

    if (data["stream"]) {
      requestOptions['headers']['Accept'] = 'text/event-stream';
      requestOptions['payload'] = JSON.stringify(data);

      const history = messages;
      const message = {
        text: '',
        user: bot,
        createdAt: new Date(),
      };

      let source = new SSE(url, requestOptions);
      source.addEventListener('message', (e) => {
        message.text += e.data;
        setMessages([...history, message]);
      });

      source.addEventListener('open', (e) => {
        console.log('SSE open: ' + e.responseCode);
      });

      source.addEventListener('abort', (e) => {
        console.log('SSE abort');
        setShowTypingIndicator(false);
      });

      source.addEventListener('readystatechange', (e) => {
        console.log('SSE ready state: ' + e.readyState);
        if (e.readyState === 2) { // CLOSED
          setShowTypingIndicator(false);
        }
      });

      source.addEventListener('error', (e) => {
        console.log('SSE error: ' + e.responseCode);
        messages.push({
          text: "Sorry, the service isn't available right now. Please try again later.",
          user: server,
          createdAt: new Date(),
        });

        setMessages([...messages]);
        setShowTypingIndicator(false);
      });

      source.stream();
    } else {
      requestOptions['body'] = JSON.stringify(data);
      fetch(url, requestOptions)
        .then(response => {
          if (!response.ok) {
            throw new Error(response.statusText);
          }
          return response.json();
        })
        .then(response => {
          let msg = response.message.content;
          messages.push({
            text: msg,
            user: bot,
            createdAt: new Date(response['created_at']),
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
          theme='#8dd4e8'
      />
  )
}

export default App
