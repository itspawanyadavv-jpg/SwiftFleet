import { ArrowLeft, Wifi, WifiOff } from 'lucide-react';
import { Link } from 'react-router-dom';
import { RiderPanel } from '../components/RiderPanel';
import { useSwiftFleetState } from '../hooks/useSwiftFleetState';

export function RiderMobilePage() {
  const state = useSwiftFleetState();

  return (
    <div
      style={{
        minHeight: '100vh',
        maxWidth: 440,
        margin: '0 auto',
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Link to="/" className="btn" style={{ textDecoration: 'none', padding: '6px 10px' }}>
          <ArrowLeft size={14} />
          Split Console
        </Link>

        <span
          className="pill mono"
          style={{
            background: state.wsConnected ? 'rgba(6, 182, 212, 0.12)' : 'rgba(244, 63, 94, 0.12)',
            color: state.wsConnected ? '#22d3ee' : '#fda4af',
          }}
        >
          {state.wsConnected ? <Wifi size={12} /> : <WifiOff size={12} />}
          {state.wsConnected ? 'STOMP LIVE' : 'CONNECTING'}
        </span>
      </div>

      <RiderPanel
        riders={state.riders}
        selectedRiderId={state.selectedRiderId}
        onSelectRider={state.setSelectedRiderId}
        orders={state.orders}
        pendingAlert={state.pendingAlert}
        telemetry={state.telemetry}
        geofenceMeters={state.config?.deliveryGeofenceMeters ?? 150}
        busyAction={state.busyAction}
        errorMsg={state.errorMsg}
        onClearError={state.clearError}
        onToggleOnline={state.toggleOnline}
        onAcceptOrder={state.acceptOrder}
        onPickupOrder={state.pickupOrder}
        onSimulateRide={state.simulateRide}
        onCompleteOrder={state.completeOrder}
        onSendRealGps={state.sendRealGps}
      />
    </div>
  );
}
