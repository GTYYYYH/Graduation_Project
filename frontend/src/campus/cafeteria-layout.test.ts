import { expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import { ResourceCache } from './ResourceCache'
it('all_nine_floor_areas', () => {
  const floorAreas=raw.locations.filter(l=>String(l.templateKey)==='cafeteria')
  expect(floorAreas.length).toBe(9)
  expect(new Set(floorAreas.map(l=>l.floorCode)).size).toBe(9)
  expect(floorAreas.every(l=>!l.locationCode.includes('kitchen'))).toBe(true)
})
it('floor_has_twelve_tables_forty_eight_chairs_and_closed_services', async()=>{
  const {createCafeteriaFloor}=await import('./templates/CafeteriaFloor')
  const pool=new ResourceCache(),room=createCafeteriaFloor(pool),counts:Record<string,number>={}
  room.group.traverse(node=>{const kind=node.userData.facility;if(typeof kind==='string')counts[kind]=(counts[kind]??0)+1;expect(node.userData.locationCode).toBeUndefined()})
  expect(counts).toMatchObject({TABLE:12,CHAIR:48,WINDOW:4,AC:4,LIGHT:12,CLOSED_SERVICES:1})
  room.dispose();expect(pool.stats().entries).toBe(0)
})
