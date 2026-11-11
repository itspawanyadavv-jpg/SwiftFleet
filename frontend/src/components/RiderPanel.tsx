import { useEffect, useRef, useState } from 'react';
import {
  Bike,
  CheckCircle2,
  KeyRound,
  LocateFixed,
  PackageCheck,
  Play,
  Power,
  ShieldAlert,
  Sparkles,
} from 'lucide-react';
import type {
  DeliveryOrder,
  DispatchAlertEvent,
  GpsTelemetryEvent,
  Rider,
} from '../types/fleet';
import { formatDistance, haversineMeters } from '../utils/geo';

interface RiderPanelProps {
  riders: Rider[];
  selectedRiderId: string;
  onSelectRider: (riderId: string) => void;
  orders: DeliveryOrder[];
  pendingAlert: DispatchAlertEvent | null;
  telemetry: GpsTelemetryEvent | null;
  geofenceMeters: number;
  busyAction: string | null;
  errorMsg: string | null;
  onClearError: () => void;
  onToggleOnline: (riderId: string, online: boolean) => void;
  onAcceptOrder: (orderId: string, riderId: string) => void;
  onPickupOrder: (orderId: string, riderId: string) => void;
  onSimulateRide: (orderId: string) => void;
  onCompleteOrder: (orderId: string, riderId: string, otp: string) => Promise<boolean>;
  onSendRealGps: (riderId: string, lat: number, lng: number, speedKmh?: number) => void;
}

export function RiderPanel({
  riders,
  selectedRiderId,
  onSelectRider,
  orders,
  pendingAlert,
  telemetry,
  geofenceMeters,
  busyAction,
  errorMsg,
  onClearError,
  onToggleOnline,
  onAcceptOrder,
  onPickupOrder,
  onSimulateRide,
  onCompleteOrder,
  onSendRealGps,
}: RiderPanelProps) {
  const [otpInput, setOtpInput] = useState('');
  const [realGpsActive, setRealGpsActive] = useState(false);
  const [gpsStatus, setGpsStatus] = useState<string | null>(null);
  const watchIdRef = useRef<number | null>(null);

  const selectedRider = riders.find((r) => r.id === selectedRiderId) || riders[0];
  const activeOrder = selectedRider?.activeOrderId
    ? orders.find((o) => o.id === selectedRider.activeOrderId) || null
    : null;
  const openOrders = orders.filter((o) => o.status === 'PENDING_DISPATCH');
  const incomingOrder =
    (pendingAlert && openOrders.find((o) => o.id === pendingAlert.orderId)) ||
    openOrders[0] ||
    null;

  useEffect(() => {
    setOtpInput('');
  }, [activeOrder?.id]);

  useEffect(() => {
    if (!realGpsActive || !selectedRider) {
      if (watchIdRef.current !== null && 'geolocation' in navigator) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
      return;
    }

    if (!('geolocation' in navigator)) {
      setGpsStatus('Geolocation not supported in this browser.');
      setRealGpsActive(false);
      return;
    }

    setGpsStatus('Acquiring GPS lock...');
    watchIdRef.current = navigator.geolocation.watchPosition(
      (pos) => {
        const speedKmh = pos.coords.speed ? Math.max(0, pos.coords.speed * 3.6) : 18;
        setGpsStatus(
          `Streaming live GPS (${pos.coords.latitude.toFixed(5)}, ${pos.coords.longitude.toFixed(5)})`
        );
        onSendRealGps(selectedRider.id, pos.coords.latitude, pos.coords.longitude, speedKmh);
      },
      (err) => {
        setGpsStatus(`GPS Error: ${err.message}`);
        setRealGpsActive(false);
      },
      { enableHighAccuracy: true, maximumAge: 1500, timeout: 8000 }
    );

    return () => {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
    };
  }, [realGpsActive, selectedRider?.id]);

  if (!selectedRider) {
    return (
      <div className="card" style={{ padding: 20 }}>
        Loading rider profile...
      </div>
    );
  }

  const distToDropMeters =
    activeOrder && telemetry && telemetry.orderId === activeOrder.id
      ? telemetry.distanceToDropMeters
      : activeOrder
        ? haversineMeters(
            selectedRider.lat,
            selectedRider.lng,
            activeOrder.dropLat,
            activeOrder.dropLng
          )
        : null;
  const insideGeofence = distToDropMeters !== null && distToDropMeters <= geofenceMeters;

  const handleOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeOrder || !otpInput.trim()) return;
    const ok = await onCompleteOrder(activeOrder.id, selectedRider.id, otpInput.trim());
    if (ok) {
      setOtpInput('');
    }
  };

  return (
    <div
      className="card"
      style={{
        padding: 16,
        borderRadius: 26,
        border: '2px solid rgba(148, 163, 184, 0.22)',
        background: 'linear-gradient(180deg, #0d1527 0%, #090f1d 100%)',
        boxShadow: '0 22px 50px rgba(0, 0, 0, 0.45)',
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
      }}
    >
      {/* Phone Notch Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div
            style={{
              width: 30,
              height: 30,
              borderRadius: 9,
              background: 'rgba(16, 185, 129, 0.16)',
              display: 'grid',
              placeItems: 'center',
              color: '#34d399',
            }}
          >
            <Bike size={16} />
          </div>
          <div>
            <div style={{ fontSize: 13, fontWeight: 700 }}>Rider App (/rider)</div>
            <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
              Partner Telemetry & OTP
            </div>
          </div>
        </div>

        <span
          className="pill mono"
          style={{
            background:
              selectedRider.status === 'IDLE'
                ? 'rgba(16, 185, 129, 0.15)'
                : selectedRider.status === 'BUSY'
                  ? 'rgba(6, 182, 212, 0.15)'
                  : 'rgba(100, 116, 139, 0.2)',
            color:
              selectedRider.status === 'IDLE'
                ? '#34d399'
                : selectedRider.status === 'BUSY'
                  ? '#22d3ee'
                  : '#94a3b8',
          }}
        >
          {selectedRider.status}
        </span>
      </div>

      {/* Rider Selector */}
      <div>
        <label style={{ fontSize: 11, color: 'var(--text-secondary)', display: 'block', marginBottom: 6 }}>
          Active Rider Profile
        </label>
        <select
          value={selectedRider.id}
          onChange={(e) => {
            onClearError();
            onSelectRider(e.target.value);
          }}
          style={{
            width: '100%',
            padding: '9px 11px',
            borderRadius: 10,
            background: 'var(--bg-elevated)',
            border: '1px solid var(--border-subtle)',
            color: 'var(--text-primary)',
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          {riders.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.status}) · {r.vehicleNumber}
            </option>
          ))}
        </select>
      </div>

      {/* Online & Real Phone GPS Controls */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <button
          className={`btn ${selectedRider.status === 'OFFLINE' ? 'btn-primary' : ''}`}
          disabled={busyAction !== null || selectedRider.status === 'BUSY'}
          onClick={() => onToggleOnline(selectedRider.id, selectedRider.status === 'OFFLINE')}
        >
          <Power size={14} />
          {selectedRider.status === 'OFFLINE' ? 'Go Online' : 'Go Offline'}
        </button>

        <button
          className={`btn ${realGpsActive ? 'btn-cyan' : ''}`}
          onClick={() => setRealGpsActive((prev) => !prev)}
          title="Streams your real phone or laptop GPS via HTML5 Geolocation over STOMP"
        >
          <LocateFixed size={14} />
          {realGpsActive ? 'Stop Real GPS' : 'Real Phone GPS'}
        </button>
      </div>

      {gpsStatus && (
        <div
          className="mono"
          style={{
            fontSize: 11,
            padding: '7px 10px',
            borderRadius: 9,
            background: 'rgba(6, 182, 212, 0.1)',
            border: '1px solid rgba(6, 182, 212, 0.28)',
            color: '#67e8f9',
          }}
        >
          {gpsStatus}
        </div>
      )}

      {errorMsg && (
        <div
          style={{
            padding: '9px 11px',
            borderRadius: 10,
            background: 'rgba(244, 63, 94, 0.14)',
            border: '1px solid rgba(244, 63, 94, 0.4)',
            color: '#fda4af',
            fontSize: 12,
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 8,
          }}
        >
          <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
            <ShieldAlert size={15} style={{ flexShrink: 0, marginTop: 1 }} />
            <span>{errorMsg}</span>
          </div>
          <button
            onClick={onClearError}
            style={{
              background: 'transparent',
              border: 'none',
              color: '#fda4af',
              cursor: 'pointer',
              fontWeight: 700,
            }}
          >
            ×
          </button>
        </div>
      )}

      {/* Active Delivery Card OR Incoming Ping */}
      {activeOrder ? (
        <div
          style={{
            background: 'var(--bg-elevated)',
            border: '1px solid var(--border-strong)',
            borderRadius: 14,
            padding: 14,
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="mono" style={{ fontSize: 12, fontWeight: 700, color: '#22d3ee' }}>
              ACTIVE · {activeOrder.id}
            </span>
            <span className="pill mono" style={{ color: '#34d399', borderColor: 'rgba(16,185,129,0.4)' }}>
              {activeOrder.status}
            </span>
          </div>

          <div>
            <div style={{ fontWeight: 700, fontSize: 14 }}>{activeOrder.customerName}</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
              {activeOrder.customerAddress}
            </div>
            <div style={{ fontSize: 12, color: '#cbd5e1', marginTop: 4 }}>
              Items: {activeOrder.itemsSummary}
            </div>
          </div>

          <div
            style={{
              padding: '8px 10px',
              borderRadius: 9,
              background: insideGeofence ? 'rgba(16, 185, 129, 0.14)' : 'rgba(245, 158, 11, 0.12)',
              border: `1px solid ${insideGeofence ? 'rgba(16, 185, 129, 0.4)' : 'rgba(245, 158, 11, 0.35)'}`,
              fontSize: 12,
              color: insideGeofence ? '#34d399' : '#fbbf24',
            }}
          >
            {distToDropMeters !== null
              ? insideGeofence
                ? `Within ${formatDistance(distToDropMeters)} of drop-off — Geofence unlocked!`
                : `${formatDistance(distToDropMeters)} from drop-off (OTP unlocks within ${geofenceMeters} m)`
              : 'Calculating proximity...'}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            <button
              className="btn btn-cyan"
              onClick={() => onSimulateRide(activeOrder.id)}
              disabled={busyAction !== null || activeOrder.status === 'DELIVERED'}
            >
              <Play size={14} />
              Simulate Ride
            </button>

            <button
              className="btn"
              onClick={() => onPickupOrder(activeOrder.id, selectedRider.id)}
              disabled={busyAction !== null || activeOrder.status !== 'ASSIGNED'}
            >
              <PackageCheck size={14} />
              Picked Up
            </button>
          </div>

          {/* 4-Digit OTP Handshake */}
          <form onSubmit={handleOtpSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 2 }}>
            <label style={{ fontSize: 11, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <KeyRound size={12} />
              Enter Customer 4-Digit OTP (test geofence/OTP guard anytime!)
            </label>
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                type="text"
                inputMode="numeric"
                maxLength={4}
                placeholder="e.g. 4821"
                value={otpInput}
                onChange={(e) => setOtpInput(e.target.value.replace(/\D/g, '').slice(0, 4))}
                className="mono"
                style={{
                  flex: 1,
                  padding: '9px 12px',
                  borderRadius: 10,
                  background: '#09101f',
                  border: '1px solid var(--border-subtle)',
                  color: 'var(--text-primary)',
                  fontSize: 16,
                  fontWeight: 700,
                  letterSpacing: '0.25em',
                  textAlign: 'center',
                }}
              />
              <button
                type="submit"
                className="btn btn-primary"
                disabled={busyAction !== null || otpInput.length !== 4}
              >
                <CheckCircle2 size={15} />
                Verify OTP
              </button>
            </div>
          </form>
        </div>
      ) : incomingOrder ? (
        <div
          style={{
            background: 'rgba(16, 185, 129, 0.08)',
            border: '1px solid rgba(16, 185, 129, 0.4)',
            borderRadius: 14,
            padding: 14,
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="mono pulse-dot" style={{ fontSize: 12, fontWeight: 700, color: '#34d399', padding: '2px 8px', borderRadius: 999, background: 'rgba(16,185,129,0.16)' }}>
              DISPATCH PING · {incomingOrder.id}
            </span>
            <span className="mono" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
              {incomingOrder.remainingKm.toFixed(2)} km trip
            </span>
          </div>

          <div>
            <div style={{ fontWeight: 700, fontSize: 14 }}>{incomingOrder.customerName}</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{incomingOrder.customerAddress}</div>
            <div style={{ fontSize: 12, color: '#cbd5e1', marginTop: 4 }}>{incomingOrder.itemsSummary}</div>
          </div>

          <button
            className="btn btn-primary"
            disabled={busyAction !== null || selectedRider.status !== 'IDLE'}
            onClick={() => onAcceptOrder(incomingOrder.id, selectedRider.id)}
          >
            <Sparkles size={15} />
            Accept Order ({selectedRider.name.split(' ')[0]})
          </button>
        </div>
      ) : (
        <div
          style={{
            border: '1px dashed var(--border-subtle)',
            borderRadius: 14,
            padding: 20,
            textAlign: 'center',
            color: 'var(--text-secondary)',
            fontSize: 13,
          }}
        >
          {selectedRider.status === 'OFFLINE'
            ? 'You are OFFLINE. Tap "Go Online" to join Redis Geo index (riders:geo).'
            : 'Online in Redis Geo index — waiting for incoming order ping or click "1-Click Live Demo".'}
        </div>
      )}

      {/* Fleet Quick Coordinates */}
      <div
        className="mono"
        style={{
          fontSize: 11,
          color: 'var(--text-muted)',
          display: 'flex',
          justifyContent: 'space-between',
          paddingTop: 6,
          borderTop: '1px solid var(--border-subtle)',
        }}
      >
        <span>LAT {selectedRider.lat.toFixed(5)}</span>
        <span>LNG {selectedRider.lng.toFixed(5)}</span>
        <span>{selectedRider.speedKmh.toFixed(0)} km/h</span>
      </div>
    </div>
  );
}
