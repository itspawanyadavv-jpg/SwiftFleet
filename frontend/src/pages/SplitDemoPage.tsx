import { HeaderBar } from '../components/HeaderBar';
import { RiderPanel } from '../components/RiderPanel';
import { LiveMapPanel } from '../components/LiveMapPanel';
import { CustomerOrderPanel } from '../components/CustomerOrderPanel';
import { InspectorDrawer } from '../components/InspectorDrawer';
import { useSwiftFleetState } from '../hooks/useSwiftFleetState';

export function SplitDemoPage() {
  const state = useSwiftFleetState();

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <HeaderBar
        config={state.config}
        health={state.health}
        wsConnected={state.wsConnected}
        busyAction={state.busyAction}
        onQuickStart={state.quickStartDemo}
        onReset={state.resetDemo}
      />

      <main className="split-grid" style={{ flex: 1, width: '100%' }}>
        <aside>
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
        </aside>

        <section className="right-stack">
          <LiveMapPanel
            config={state.config}
            riders={state.riders}
            selectedRiderId={state.selectedRiderId}
            activeOrder={state.activeOrder}
            telemetry={state.telemetry}
            customDrop={state.customDrop}
            onMapClickDrop={state.setCustomDrop}
          />

          <div className="bottom-duo">
            <CustomerOrderPanel
              config={state.config}
              orders={state.orders}
              activeOrder={state.activeOrder}
              onSelectOrder={state.setSelectedOrderId}
              customDrop={state.customDrop}
              onPickPresetDrop={(latLng) => state.setCustomDrop(latLng)}
              raceReport={state.raceReport}
              busyAction={state.busyAction}
              onCreateOrder={state.createOrder}
              onSimulateRaceCondition={state.simulateRaceCondition}
              onSyncDrc={state.syncDrc}
            />

            <InspectorDrawer entries={state.inspector} />
          </div>
        </section>
      </main>
    </div>
  );
}
