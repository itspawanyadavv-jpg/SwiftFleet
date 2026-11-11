import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import type {
  DeliveryOrder,
  DispatchAlertEvent,
  GpsTelemetryEvent,
  InspectorLogEntry,
  Rider,
} from '../types/fleet';
import { resolveWsBrokerUrl } from '../utils/geo';

interface SocketHandlers {
  activeRiderId?: string;
  activeOrderId?: string | null;
  onFleetUpdate: (riders: Rider[]) => void;
  onOrdersUpdate: (orders: DeliveryOrder[]) => void;
  onOrderDetail?: (order: DeliveryOrder) => void;
  onTelemetry?: (telemetry: GpsTelemetryEvent) => void;
  onDispatchAlert?: (alert: DispatchAlertEvent) => void;
  onInspectorEntry?: (entry: InspectorLogEntry) => void;
}

export function useSwiftFleetSocket(handlers: SocketHandlers) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  useEffect(() => {
    const client = new Client({
      brokerURL: resolveWsBrokerUrl(),
      reconnectDelay: 2500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/fleet', (msg) => {
          handlersRef.current.onFleetUpdate(JSON.parse(msg.body) as Rider[]);
        });
        client.subscribe('/topic/orders', (msg) => {
          handlersRef.current.onOrdersUpdate(JSON.parse(msg.body) as DeliveryOrder[]);
        });
        client.subscribe('/topic/inspector', (msg) => {
          handlersRef.current.onInspectorEntry?.(JSON.parse(msg.body) as InspectorLogEntry);
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, []);

  useEffect(() => {
    const client = clientRef.current;
    if (!connected || !client || !handlers.activeRiderId) {
      return;
    }
    const sub = client.subscribe(`/topic/dispatch/${handlers.activeRiderId}`, (msg) => {
      handlersRef.current.onDispatchAlert?.(JSON.parse(msg.body) as DispatchAlertEvent);
    });
    return () => sub.unsubscribe();
  }, [connected, handlers.activeRiderId]);

  useEffect(() => {
    const client = clientRef.current;
    if (!connected || !client || !handlers.activeOrderId) {
      return;
    }
    const orderSub = client.subscribe(`/topic/order/${handlers.activeOrderId}`, (msg) => {
      handlersRef.current.onOrderDetail?.(JSON.parse(msg.body) as DeliveryOrder);
    });
    const telemetrySub = client.subscribe(
      `/topic/order/${handlers.activeOrderId}/telemetry`,
      (msg) => {
        handlersRef.current.onTelemetry?.(JSON.parse(msg.body) as GpsTelemetryEvent);
      }
    );
    return () => {
      orderSub.unsubscribe();
      telemetrySub.unsubscribe();
    };
  }, [connected, handlers.activeOrderId]);

  const sendLocationFrame = (payload: {
    riderId: string;
    lat: number;
    lng: number;
    speedKmh?: number;
    bearingDeg?: number;
  }) => {
    const client = clientRef.current;
    if (!client || !client.connected) {
      return false;
    }
    client.publish({
      destination: '/app/rider/location',
      body: JSON.stringify(payload),
    });
    return true;
  };

  return { connected, sendLocationFrame };
}

