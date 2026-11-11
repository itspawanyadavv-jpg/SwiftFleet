import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { SplitDemoPage } from './pages/SplitDemoPage';
import { RiderMobilePage } from './pages/RiderMobilePage';

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<SplitDemoPage />} />
        <Route path="/rider" element={<RiderMobilePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;

