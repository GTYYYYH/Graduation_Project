import { expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import { ResourceCache } from './ResourceCache'
import type { Object3D } from 'three'
it('every_seat_is_supported_outside_the_central_void',async()=>{
  const {createGymInterior}=await import('./templates/GymBuilding'),pool=new ResourceCache(),room=createGymInterior(pool,'gym-gallery')
  const seats:Object3D[]=[],supports:Object3D[]=[]
  room.group.traverse(n=>{if(n.userData.facility==='SEAT')seats.push(n);if(['GALLERY_DECK','GALLERY_TIER'].includes(String(n.userData.facility)))supports.push(n)})
  expect(seats).toHaveLength(32)
  for(const seat of seats)expect(supports.some(deck=>
    Math.abs(seat.position.x-deck.position.x)+seat.scale.x/2<=deck.scale.x/2+.001
    && Math.abs(seat.position.z-deck.position.z)+seat.scale.z/2<=deck.scale.z/2+.001
    && Math.abs((seat.position.y-seat.scale.y/2)-(deck.position.y+deck.scale.y/2))<.1)).toBe(true)
  expect(supports.every(deck=>Math.abs(deck.position.x)-deck.scale.x/2>=9)).toBe(true)
  room.dispose();expect(pool.stats().entries).toBe(0)
})
it('two_floor_areas_only',async()=>{
  const floorAreas=raw.locations.filter(l=>String(l.templateKey).startsWith('gym-'))
  expect(floorAreas.length).toBe(2)
  const {createGymInterior}=await import('./templates/GymBuilding')
  const pool=new ResourceCache(),room=createGymInterior(pool,'gym-gallery'),standSeats:unknown[]=[]
  room.group.traverse(n=>{if(n.userData.facility==='SEAT')standSeats.push(n)})
  expect(standSeats.length).toBe(32)
  room.dispose();expect(pool.stats().entries).toBe(0)
})
it('both_views_have_complete_void_stairs_and_only_five_business_groups',async()=>{
  const {createGymInterior}=await import('./templates/GymBuilding'),pool=new ResourceCache()
  for(const kind of ['gym-court','gym-gallery'] as const){
    const room=createGymInterior(pool,kind),counts:Record<string,number>={}
    room.group.traverse(n=>{const f=n.userData.facility;if(typeof f==='string')counts[f]=(counts[f]??0)+1})
    expect(counts).toMatchObject({BASKET:2,COURT_LIGHT:12,GALLERY_LIGHT:6,COURT_AC:4,GALLERY_AC:2,SEAT:32,STAIR:2})
    expect(room.group.userData.centralVoid).toBe(true);room.dispose()
  }
  expect(pool.stats().entries).toBe(0)
})
