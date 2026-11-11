import { useCallback, useEffect, useState } from 'react';
import { ApiError, fleetApi } from '../api/fleetApi';
import type {
  DeliveryOrder,
  DispatchAlertEvent,
  GpsTelemetryEvent,
  HealthInfo,
  InspectorLogEntry,
  LatLng,
  RaceConditionReport,
  Rider,
  StoreConfig,
} from '../types/fleet';
import { useSwiftFleetSocket } from './useSwiftFleetSocket';

export function useSwiftFleetState() {
  const [config, setConfig] = useState<StoreConfig | null>(null);
  const [health, setHealth] = useState<HealthInfo | null>(null);
  const [riders, setRiders] = useState<Rider[]>([]);
  const [orders, setOrders] = useState<DeliveryOrder[]>([]);
  const [inspector, setInspector] = useState<InspectorLogEntry[]>([]);
  const [selectedRiderId, setSelectedRiderId] = useState<string>('rdr-1');
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [telemetry, setTelemetry] = useState<GpsTelemetryEvent | null>(null);
  const [pendingAlert, setPendingAlert] = useState<DispatchAlertEvent | null>(null);
  const [raceReport, setRaceReport] = useState<RaceConditionReport | null>(null);
  const [customDrop, setCustomDrop] = useState<LatLng>({ lat: 28.6549, lng: 77.4512 });
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const loadInitial = useCallback(async () => {
    try {
      const [cfg, hlth, riderList, orderList, logs] = await Promise.all([
        fleetApi.getConfig(),
        fleetApi.getHealth(),
        fleetApi.listRiders(),
        fleetApi.listOrders(),
        fleetApi.getInspectorLogs(),
      ]);
      setConfig(cfg);
      setHealth(hlth);
      setRiders(riderList);
      setOrders(orderList);
      setInspector(logs);
      setCustomDrop({ lat: cfg.storeLat + 0.0082, lng: cfg.storeLng + 0.0068 });
      if (riderList.length > 0) {
        setSelectedRiderId((prev) =>
          riderList.some((r) => r.id === prev) ? prev : riderList[0].id
        );
      }
      if (orderList.length > 0) {
        setSelectedOrderId((prev) =>
          prev && orderList.some((o) => o.id === prev) ? prev : orderList[0].id
        );
      }
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Unable to reach SwiftFleet backend');
    }
  }, []);

  useEffect(() => {
    loadInitial();
  }, [loadInitial]);

  const activeOrder =
    (selectedOrderId ? orders.find((o) => o.id === selectedOrderId) : null) ||
    orders[0] ||
    null;

  const { connected: wsConnected, sendLocationFrame } = useSwiftFleetSocket({
    activeRiderId: selectedRiderId,
    activeOrderId: activeOrder?.id ?? null,
    onFleetUpdate: setRiders,
    onOrdersUpdate: (nextOrders) => {
      setOrders(nextOrders);
      if (nextOrders.length > 0) {
        setSelectedOrderId((prev) =>
          prev && nextOrders.some((o) => o.id === prev) ? prev : nextOrders[0].id
        );
      }
    },
    onOrderDetail: (updated) => {
      setOrders((prev) => {
        const idx = prev.findIndex((o) => o.id === updated.id);
        if (idx === -1) return [updated, ...prev];
        const copy = [...prev];
        copy[idx] = updated;
        return copy;
      });
    },
    onTelemetry: (event) => setTelemetry(event),
    onDispatchAlert: (alert) => setPendingAlert(alert),
    onInspectorEntry: (entry) =>
      setInspector((prev) => {
        if (prev.some((p) => p.seq === entry.seq)) return prev;
        return [...prev.slice(-119), entry];
      }),
  });

  const runAction = async <T,>(key: string, fn: () => Promise<T>): Promise<T | null> => {
    setBusyAction(key);
    setErrorMsg(null);
    try {
      return await fn();
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMsg(err.message);
      } else if (err instanceof Error) {
        setErrorMsg(err.message);
      } else {
        setErrorMsg('Unexpected action error');
      }
      return null;
    } finally {
      setBusyAction(null);
    }
  };

  const toggleOnline = (riderId: string, online: boolean) =>
    runAction('toggle-online', async () => {
      const updated = await fleetApi.setRiderOnline(riderId, online);
      setRiders((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    });

  const createOrder = (payload: {
    customerName: string;
    customerAddress: string;
    itemsSummary: string;
    dropLat: number;
    dropLng: number;
  }) =>
    runAction('create-order', async () => {
      const created = await fleetApi.createOrder(payload);
      setTelemetry(null);
      setOrders((prev) => [created, ...prev.filter((o) => o.id !== created.id)]);
      setSelectedOrderId(created.id);
      return created;
    });

  const acceptOrder = (orderId: string, riderId: string) =>
    runAction('accept-order', async () => {
      const assigned = await fleetApi.acceptOrder(orderId, riderId);
      setPendingAlert(null);
      setSelectedOrderId(assigned.id);
      setTelemetry(null);
      setOrders((prev) => prev.map((o) => (o.id === assigned.id ? assigned : o)));
      return assigned;
    });

  const pickupOrder = (orderId: string, riderId: string) =>
    runAction('pickup-order', async () => {
      const updated = await fleetApi.pickupOrder(orderId, riderId);
      setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
      return updated;
    });

  const simulateRide = (orderId: string) =>
    runAction('simulate-ride', async () => {
      await fleetApi.simulateOrderRide(orderId, false);
    });

  const completeOrder = async (orderId: string, riderId: string, otp: string) => {
    const res = await runAction('complete-order', async () => {
      const completed = await fleetApi.completeOrder(orderId, riderId, otp);
      setOrders((prev) => prev.map((o) => (o.id === completed.id ? completed : o)));
      return completed;
    });
    return res !== null;
  };

  const simulateRaceCondition = () =>
    runAction('race-condition', async () => {
      const open = orders.find((o) => o.status === 'PENDING_DISPATCH');
      const report = await fleetApi.simulateRaceCondition(open?.id);
      setRaceReport(report);
      const updatedOrders = await fleetApi.listOrders();
      setOrders(updatedOrders);
      setSelectedOrderId(report.orderId);
      if (report.winnerRiderId) {
        setSelectedRiderId(report.winnerRiderId);
      }
      return report;
    });

  const quickStartDemo = () =>
    runAction('quick-start', async () => {
      const assigned = await fleetApi.quickStartDemo();
      setTelemetry(null);
      setPendingAlert(null);
      setOrders((prev) => [assigned, ...prev.filter((o) => o.id !== assigned.id)]);
      setSelectedOrderId(assigned.id);
      if (assigned.assignedRiderId) {
        setSelectedRiderId(assigned.assignedRiderId);
      }
      return assigned;
    });

  const resetDemo = () =>
    runAction('reset-demo', async () => {
      const res = await fleetApi.resetDemo();
      setRiders(res.riders);
      setOrders([]);
      setSelectedOrderId(null);
      setTelemetry(null);
      setPendingAlert(null);
      setRaceReport(null);
      const logs = await fleetApi.getInspectorLogs();
      setInspector(logs);
      if (res.riders.length > 0) {
        setSelectedRiderId(res.riders[0].id);
      }
    });

  const syncDrc = async (url: string) => {
    const updated = await fleetApi.syncDrcStore(url);
    setConfig(updated);
  };

  const sendRealGps = (riderId: string, lat: number, lng: number, speedKmh?: number) => {
    const sentOverWs = sendLocationFrame({ riderId, lat, lng, speedKmh });
    if (!sentOverWs) {
      void fleetApi.updateRiderLocation(riderId, lat, lng, speedKmh);
    }
  };

  return {
    config,
    health,
    wsConnected,
    riders,
    orders,
    activeOrder,
    inspector,
    selectedRiderId,
    setSelectedRiderId,
    setSelectedOrderId,
    telemetry,
    pendingAlert,
    raceReport,
    customDrop,
    setCustomDrop,
    busyAction,
    errorMsg,
    clearError: () => setErrorMsg(null),
    toggleOnline,
    createOrder,
    acceptOrder,
    pickupOrder,
    simulateRide,
    completeOrder,
    simulateRaceCondition,
    quickStartDemo,
    resetDemo,
    syncDrc,
    sendRealGps,
  };
}

