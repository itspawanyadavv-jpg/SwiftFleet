import { Terminal } from 'lucide-react';
import type { InspectorLogEntry } from '../types/fleet';

interface InspectorDrawerProps {
  entries: InspectorLogEntry[];
}

const CATEGORY_COLORS: Record<string, string> = {
  REDIS_GEO: '#34d399',
  REDIS_LOCK: '#fbbf24',
  STOMP_GPS: '#22d3ee',
  STOMP_DISPATCH: '#818cf8',
  OTP_HANDSHAKE: '#34d399',
  GEOFENCE_GUARD: '#fb7185',
  SIMULATOR: '#c084fc',
  DRC_SYNC: '#60a5fa',
  BOOTSTRAP: '#94a3b8',
};

export function InspectorDrawer({ entries }: InspectorDrawerProps) {
  const ordered = [...entries].reverse();

  return (
    <div
      className="card"
      style={{
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        minHeight: 340,
        maxHeight: 540,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Terminal size={16} color="#22d3ee" />
          <span style={{ fontWeight: 700, fontSize: 14 }}>
            Live Redis & STOMP Architecture Inspector
          </span>
        </div>
        <span className="mono" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
          {entries.length} event(s)
        </span>
      </div>

      <div
        className="mono"
        style={{
          flex: 1,
          overflowY: 'auto',
          background: '#050912',
          borderRadius: 10,
          border: '1px solid var(--border-subtle)',
          padding: 10,
          display: 'flex',
          flexDirection: 'column',
          gap: 7,
          fontSize: 11.5,
        }}
      >
        {ordered.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', padding: 12 }}>
            Waiting for Redis Geo & STOMP events...
          </div>
        ) : (
          ordered.map((entry) => {
            const color = CATEGORY_COLORS[entry.category] || '#cbd5e1';
            const time = new Date(entry.timestamp).toLocaleTimeString([], {
              hour12: false,
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            });
            return (
              <div
                key={entry.seq}
                style={{
                  padding: '7px 9px',
                  borderRadius: 8,
                  background: 'rgba(15, 22, 38, 0.72)',
                  borderLeft: `3px solid ${color}`,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                  <span style={{ color, fontWeight: 700 }}>{entry.category}</span>
                  <span style={{ color: 'var(--text-muted)' }}>
                    {entry.latencyMs}ms · {time}
                  </span>
                </div>
                <div style={{ color: '#e2e8f0', marginTop: 3, wordBreak: 'break-word' }}>
                  {entry.command}
                </div>
                <div style={{ color: 'var(--text-secondary)', fontSize: 11, marginTop: 2 }}>
                  {entry.detail}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
