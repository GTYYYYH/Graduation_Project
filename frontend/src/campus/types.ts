import type { CatalogVo } from '../api/account'
export type Vec3 = [number, number, number]
export interface CameraAnchor { position: Vec3; target: Vec3 }
export type CampusTarget = { kind: 'OVERVIEW' } | { kind: 'BUILDING'; buildingId: string } | { kind: 'FLOOR'; floorId: string } | { kind: 'LOCATION'; locationId: string }
export interface ManifestLocation {
  locationCode: string; floorCode: string; templateKey: 'dorm-room' | 'dorm-lobby' | 'dorm-duty' | 'classroom' | 'office' | 'teaching-restroom' | 'teaching-lobby' | 'cafeteria' | 'gym-court' | 'gym-gallery'
  doorKey: string | null; entryAnchor: CameraAnchor; interiorAnchor: CameraAnchor
  roomSize?: [number, number]; layoutSlot?: number
  origin: Vec3; rotationY: number
}
export interface CampusManifest {
  configVersion: string; contentVersion: string; resources: { key: string; url: string; contentHash: string; byteSize: number }[]
  buildings: { buildingCode: string; exteriorResourceKey: string; floorCodes: string[]; origin?: Vec3 }[]
  locations: ManifestLocation[]
}
export interface BoundLocation { location: CatalogVo['locations'][number]; geometry: ManifestLocation }
export interface CampusBinding { building: CatalogVo['buildings'][number]; buildings: CatalogVo['buildings']; floors: CatalogVo['floors']; locations: BoundLocation[] }
export type ObservationLevel = 'OVERVIEW' | 'BUILDING' | 'FLOOR' | 'DOOR' | 'INTERIOR'
export interface RendererState { level: ObservationLevel; locationId: string | null; loading: boolean; error: string; elapsedMs?: number; condition?: 'cold' | 'warm'; failure?: 'INTERIOR' | 'MODULE' | 'WEBGL' }
