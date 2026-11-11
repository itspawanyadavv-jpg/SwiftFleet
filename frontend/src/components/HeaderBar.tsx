import { Activity, Database, Play, RotateCcw, Smartphone, Wifi, WifiOff, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { HealthInfo, StoreConfig } from '../types/fleet';

interface HeaderBarProps {
  config: StoreConfig | null;
  health: HealthInfo | null;
  wsConnected: boolean;
  busyAction: string | null;
  onQuickStart: () => void;
  onReset: () => void;
}

export function HeaderBar({
  config,
  health,
  wsConnected,
  busyAction,
  onQuickStart,
  onReset,
}: HeaderBarProps) {
  const isRedisLive = health?.redisLive ?? config?.storeMode === 'REDIS_LIVE';

  return (
    <header
      style={{
        borderBottom: '1px solid var(--border-subtle)',
        background: 'rgba(15, 22, 38, 0.92)',
        backdropFilter: 'blur(12px)',
        position: 'sticky',
        top: 0,
        zIndex: 40,
      }}
    >
      <div
        style={{
          maxWidth: 1600,
          margin: '0 auto',
          padding: '12px 16px',
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              width: 38,
              height: 38,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #10b981, #06b6d4)',
              display: 'grid',
              placeItems: 'center',
              color: '#041410',
              fontWeight: 800,
            }}
          >
            <Zap size={20} strokeWidth={2.5} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em' }}>
                SwiftFleet
              </span>
              <span
                className="pill mono"
                style={{
                  background: 'rgba(99, 102, 241, 0.14)',
                  borderColor: 'rgba(99, 102, 241, 0.35)',
                  color: '#a5b4fc',
                }}
              >
                Spring Boot 3 + STOMP + Redis Geo
              </span>
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
              {config ? `${config.storeName} · ${config.dispatchRadiusKm} km Radius` : 'Loading hub...'}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
          <span
            className="pill mono"
            style={{
              background: isRedisLive ? 'rgba(16, 185, 129, 0.12)' : 'rgba(245, 158, 11, 0.12)',
              borderColor: isRedisLive ? 'rgba(16, 185, 129, 0.4)' : 'rgba(245, 158, 11, 0.4)',
              color: isRedisLive ? '#34d399' : '#fbbf24',
            }}
            title={
              isRedisLive
                ? 'Connected to live Redis Geo & Distributed Lock store'
                : 'Using thread-safe in-memory Geo/Lock store (start Docker Redis for live Redis)'
            }
          >
            <Database size={12} />
            {isRedisLive ? 'REDIS LIVE' : 'IN-MEMORY FALLBACK'}
          </span>

          <span
            className="pill mono"
            style={{
              background: wsConnected ? 'rgba(6, 182, 212, 0.12)' : 'rgba(244, 63, 94, 0.12)',
              borderColor: wsConnected ? 'rgba(6, 182, 212, 0.4)' : 'rgba(244, 63, 94, 0.4)',
              color: wsConnected ? '#22d3ee' : '#fda4af',
            }}
          >
            {wsConnected ? <Wifi size={12} /> : <WifiOff size={12} />}
            {wsConnected ? 'STOMP CONNECTED' : 'STOMP RECONNECTING'}
          </span>

          <Link to="/rider" className="btn" style={{ textDecoration: 'none' }}>
            <Smartphone size={14} />
            Mobile /rider
          </Link>

          <button
            className="btn btn-primary"
            onClick={onQuickStart}
            disabled={busyAction !== null}
            title="Places an order, matches nearest rider via GEOSEARCH, acquires Redis lock, and runs 20s simulation"
          >
            {busyAction === 'quick-start' ? <Activity size={14} /> : <Play size={14} />}
            1-Click Live Demo
          </button>

          <button className="btn" onClick={onReset} disabled={busyAction !== null}>
            <RotateCcw size={14} />
            Reset Fleet
          </button>
        </div>
      </div>
    </header>
  );
}
