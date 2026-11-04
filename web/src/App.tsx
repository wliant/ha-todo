import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { ListsPage } from './pages/ListsPage'
import { LoginPage } from './pages/LoginPage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/lists"
        element={
          <ProtectedRoute>
            <ListsPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/lists" replace />} />
    </Routes>
  )
}
