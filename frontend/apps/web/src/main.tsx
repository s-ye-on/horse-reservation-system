import { StrictMode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { webQueryClient } from './api/web-query-client'
import './index.css'
import App from './app'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={webQueryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
