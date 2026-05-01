import React, { createContext, useContext, useEffect, useState } from 'react'
import client from '../api/client'
import { User } from '../types'

interface AuthContextType {
  user: User | null
  token: string | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  isLoading: boolean
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const storedToken = localStorage.getItem('token')
    const storedUser = localStorage.getItem('user')

    if (storedToken && storedUser) {
      setToken(storedToken)
      setUser(JSON.parse(storedUser))
      restoreSession(storedToken)
    } else {
      setIsLoading(false)
    }
  }, [])

  const restoreSession = async (storedToken: string) => {
    try {
      const response = await client.get('/auth/me', {
        headers: { Authorization: `Bearer ${storedToken}` },
      })
      setUser(response.data)
      setToken(storedToken)
    } catch (error) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      setToken(null)
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }

  const login = async (username: string, password: string) => {
    const response = await client.post('/auth/login', { username, password })
    const { access_token, user: userData } = response.data

    localStorage.setItem('token', access_token)
    localStorage.setItem('user', JSON.stringify(userData))

    setToken(access_token)
    setUser(userData)
  }

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, token, login, logout, isLoading }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
