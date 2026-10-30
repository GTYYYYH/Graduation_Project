import type { ResourceCache } from '../ResourceCache'
import { createTeachingBuilding } from './TeachingBuilding'
export function createCafeteriaBuilding(pool:ResourceCache) { return createTeachingBuilding(pool,3,32,30) }
