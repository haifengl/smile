/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
import { useEffect, useState } from 'react'
import { fetchEventSource, EventStreamContentType } from '@microsoft/fetch-event-source';
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
      "model": "llama3.3",
      "threadId": threadId,
      "stream": false,
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

    const url = '/api/chat';
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
            // everything's good
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
            text: "Sorry, the service isn't available right now. Please try again later.",
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
        .then(response => {
          messages.push({
            text: response.message.content,
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
