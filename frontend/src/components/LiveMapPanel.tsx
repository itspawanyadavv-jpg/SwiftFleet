import { useEffect, useRef } from 'react';
import L from 'leaflet';
import { MapPin, Navigation } from 'lucide-react';
import type { DeliveryOrder, GpsTelemetryEvent, LatLng, Rider, StoreConfig } from '../types/fleet';

interface LiveMapPanelProps {
  config: StoreConfig | null;
  riders: Rider[];
  selectedRiderId: string;
  activeOrder: DeliveryOrder | null;
  telemetry: GpsTelemetryEvent | null;
  customDrop: LatLng;
  onMapClickDrop: (latLng: LatLng) => void;
}

export function LiveMapPanel({
  config,
  riders,
  selectedRiderId,
  activeOrder,
  telemetry,
  customDrop,
  onMapClickDrop,
}: LiveMapPanelProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const riderMarkersRef = useRef<Map<string, L.Marker>>(new Map());
  const hubLayerRef = useRef<L.LayerGroup | null>(null);
  const orderLayerRef = useRef<L.LayerGroup | null>(null);
  const onMapClickRef = useRef(onMapClickDrop);
  onMapClickRef.current = onMapClickDrop;

  useEffect(() => {
    if (!containerRef.current || mapRef.current) {
      return;
    }
    const lat = config?.storeLat ?? 28.646705;
    const lng = config?.storeLng ?? 77.444478;

    const map = L.map(containerRef.current, {
      center: [lat, lng],
      zoom: 14,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);

    const hubLayer = L.layerGroup().addTo(map);
    const orderLayer = L.layerGroup().addTo(map);
    hubLayerRef.current = hubLayer;
    orderLayerRef.current = orderLayer;

    map.on('click', (e: L.LeafletMouseEvent) => {
      onMapClickRef.current({
        lat: Number(e.latlng.lat.toFixed(6)),
        lng: Number(e.latlng.lng.toFixed(6)),
      });
    });

    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
      riderMarkersRef.current.clear();
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const hubLayer = hubLayerRef.current;
    if (!map || !hubLayer || !config) {
      return;
    }

    hubLayer.clearLayers();
    L.circle([config.storeLat, config.storeLng], {
      radius: config.dispatchRadiusKm * 1000,
      color: '#6366f1',
      weight: 1.5,
      dashArray: '6 6',
      fillColor: '#6366f1',
      fillOpacity: 0.06,
    }).addTo(hubLayer);

    const hubIcon = L.divIcon({
      className: 'sf-marker-wrap',
      iconSize: [38, 38],
      iconAnchor: [19, 19],
      html: `<div style="width:38px;height:38px;border-radius:11px;background:#4f46e5;color:#fff;display:grid;place-items:center;font-weight:800;font-size:11px;border:2px solid #fff;box-shadow:0 4px 12px rgba(0,0,0,.45)">HUB</div>`,
    });
    L.marker([config.storeLat, config.storeLng], { icon: hubIcon })
      .bindTooltip(`${config.storeName} (${config.dispatchRadiusKm} km dispatch zone)`)
      .addTo(hubLayer);
  }, [config]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    const activeIds = new Set(riders.map((r) => r.id));
    for (const [id, marker] of riderMarkersRef.current.entries()) {
      if (!activeIds.has(id)) {
        marker.remove();
        riderMarkersRef.current.delete(id);
      }
    }

    riders.forEach((rider) => {
      const bg =
        rider.status === 'IDLE'
          ? '#10b981'
          : rider.status === 'BUSY'
            ? '#06b6d4'
            : '#64748b';
      const ring = rider.id === selectedRiderId ? 'box-shadow:0 0 0 4px rgba(251,191,36,0.75);' : '';
      const initials = rider.name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .slice(0, 2)
        .toUpperCase();

      const icon = L.divIcon({
        className: 'sf-marker-wrap',
        iconSize: [34, 34],
        iconAnchor: [17, 17],
        html: `<div class="sf-scooter-badge" style="background:${bg};${ring}">${initials}</div>`,
      });

      const existing = riderMarkersRef.current.get(rider.id);
      if (existing) {
        existing.setLatLng([rider.lat, rider.lng]);
        existing.setIcon(icon);
        existing.setTooltipContent(
          `${rider.name} (${rider.status}) · ${rider.speedKmh.toFixed(0)} km/h`
        );
      } else {
        const marker = L.marker([rider.lat, rider.lng], { icon })
          .bindTooltip(`${rider.name} (${rider.status})`)
          .addTo(map);
        riderMarkersRef.current.set(rider.id, marker);
      }
    });
  }, [riders, selectedRiderId]);

  useEffect(() => {
    const orderLayer = orderLayerRef.current;
    if (!orderLayer) {
      return;
    }
    orderLayer.clearLayers();

    const dropPoint = activeOrder
      ? { lat: activeOrder.dropLat, lng: activeOrder.dropLng }
      : customDrop;
    const insideFence = telemetry?.insideDeliveryGeofence ?? false;
    const geofenceRadius = config?.deliveryGeofenceMeters ?? 150;

    L.circle([dropPoint.lat, dropPoint.lng], {
      radius: geofenceRadius,
      color: insideFence ? '#10b981' : '#f59e0b',
      weight: 2,
      fillColor: insideFence ? '#10b981' : '#f59e0b',
      fillOpacity: 0.13,
    }).addTo(orderLayer);

    const dropIcon = L.divIcon({
      className: 'sf-marker-wrap',
      iconSize: [34, 34],
      iconAnchor: [17, 17],
      html: `<div style="width:32px;height:32px;border-radius:50%;background:${activeOrder ? '#f43f5e' : '#f59e0b'};color:#fff;display:grid;place-items:center;font-weight:800;font-size:11px;border:2px solid #fff;box-shadow:0 4px 12px rgba(0,0,0,.45)">DROP</div>`,
    });

    L.marker([dropPoint.lat, dropPoint.lng], { icon: dropIcon })
      .bindTooltip(
        activeOrder
          ? `${activeOrder.customerName} — ${activeOrder.customerAddress}`
          : 'Selected Drop-off Pin (click anywhere on map to move)'
      )
      .addTo(orderLayer);

    if (activeOrder?.routePolyline && activeOrder.routePolyline.length > 1) {
      const coords = activeOrder.routePolyline.map((pt) => [pt.lat, pt.lng] as [number, number]);
      L.polyline(coords, {
        color: '#22d3ee',
        weight: 4,
        opacity: 0.85,
        dashArray: activeOrder.status === 'PENDING_DISPATCH' ? '8 8' : undefined,
      }).addTo(orderLayer);
    }
  }, [activeOrder, customDrop, telemetry, config]);

  return (
    <div className="card" style={{ padding: 12, position: 'relative', display: 'flex', flexDirection: 'column', minHeight: 410 }}>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          marginBottom: 10,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Navigation size={16} color="#22d3ee" />
          <span style={{ fontWeight: 700, fontSize: 14 }}>Live Fleet & Customer Telemetry Map</span>
          <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
            (Click map to move drop-off pin)
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 11 }}>
          <span style={{ color: '#34d399' }}>● Idle</span>
          <span style={{ color: '#22d3ee' }}>● Busy</span>
          <span style={{ color: '#94a3b8' }}>● Offline</span>
          <span style={{ color: '#818cf8' }}>◌ 3 km GEOSEARCH</span>
        </div>
      </div>

      <div style={{ position: 'relative', flex: 1, minHeight: 340 }}>
        <div ref={containerRef} style={{ height: '100%', minHeight: 340 }} />

        {activeOrder && (
          <div
            style={{
              position: 'absolute',
              top: 12,
              right: 12,
              zIndex: 500,
              background: 'rgba(15, 22, 38, 0.92)',
              backdropFilter: 'blur(8px)',
              border: '1px solid var(--border-strong)',
              borderRadius: 12,
              padding: '10px 14px',
              minWidth: 230,
              boxShadow: '0 10px 25px rgba(0,0,0,0.45)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <span className="mono" style={{ fontSize: 12, fontWeight: 700, color: '#22d3ee' }}>
                {activeOrder.id}
              </span>
              <span
                className="pill mono"
                style={{
                  background: 'rgba(16, 185, 129, 0.14)',
                  borderColor: 'rgba(16, 185, 129, 0.4)',
                  color: '#34d399',
                }}
              >
                {activeOrder.status}
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginTop: 10 }}>
              <div>
                <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>ETA</div>
                <div style={{ fontSize: 15, fontWeight: 700 }}>
                  {telemetry?.etaMinutes ?? activeOrder.etaMinutes} min
                </div>
              </div>
              <div>
                <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>REMAINING</div>
                <div style={{ fontSize: 15, fontWeight: 700 }}>
                  {(telemetry?.remainingKm ?? activeOrder.remainingKm).toFixed(2)} km
                </div>
              </div>
              <div>
                <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>SPEED</div>
                <div style={{ fontSize: 15, fontWeight: 700 }}>
                  {telemetry ? `${telemetry.speedKmh.toFixed(0)}` : '0'} km/h
                </div>
              </div>
            </div>

            <div
              style={{
                marginTop: 8,
                paddingTop: 8,
                borderTop: '1px solid var(--border-subtle)',
                fontSize: 11,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                color: telemetry?.insideDeliveryGeofence ? '#34d399' : 'var(--text-secondary)',
              }}
            >
              <MapPin size={12} />
              {telemetry
                ? telemetry.insideDeliveryGeofence
                  ? `Inside ${config?.deliveryGeofenceMeters ?? 150}m geofence — Ready for OTP`
                  : `${Math.round(telemetry.distanceToDropMeters)}m from drop-off`
                : `Route: ${activeOrder.routeSource}`}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
