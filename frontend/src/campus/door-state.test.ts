import { describe, expect, it } from 'vitest'
import { createDormRoom } from './templates/DormRoom'
import { ResourceCache } from './ResourceCache'
import { CameraTransition } from './CameraTransition'
import type { CameraAnchor } from './types'

const a: CameraAnchor = { position: [0, 1.6, -2], target: [0, 1.6, 0] }
const b: CameraAnchor = { position: [0, 2.2, 1], target: [0, 1.2, 4] }
describe('independent doors and room facilities', () => {
  it('cutaway_hides_near_wall_outside_room_and_restores_it_without_changing_door', () => {
    const pool = new ResourceCache(), room = createDormRoom(pool)
    const wall = room.group.children.find(node => node.userData.cutawaySide === 'right')!
    room.updateCutaway([8, 1.5, 3]); expect(wall.visible).toBe(false)
    room.updateCutaway([0, 6, 2]); expect(wall.visible).toBe(true)
    expect(room.door.open).toBe(false); room.dispose()
  })
  it('doors_are_instance_specific', () => {
    const pool = new ResourceCache(), roomA = createDormRoom(pool), roomB = createDormRoom(pool)
    roomA.setDoorProgress(1)
    const doorB = roomB.door
    expect(roomA.door.open).toBe(true); expect(doorB.open).toBe(false)
    expect(roomA.door.pivot.rotation.y).toBeCloseTo(-Math.PI / 2)
    expect(doorB.pivot.rotation.y).toBe(0)
    roomA.dispose(); expect(doorB.open).toBe(false)
    expect(pool.stats().references).toBeGreaterThan(0)
    roomB.dispose(); expect(pool.stats().entries).toBe(0)
  })
  it('dorm_room_has_two_bunk_desks_and_separate_bathroom_facilities', () => {
    const pool = new ResourceCache(), room = createDormRoom(pool), counts: Record<string, number> = {}
    room.group.traverse(node => { const kind = node.userData.facility; if (typeof kind === 'string') counts[kind] = (counts[kind] ?? 0) + 1 })
    expect(counts).toEqual({ BED: 2, DESK: 2, TOILET: 1, SHOWER: 1, BASIN: 1, AC: 1, ROOM_LIGHT: 1, BATH_LIGHT: 1, SOCKET: 2 })
    room.dispose()
  })
})
describe('camera and door animation generation', () => {
  it('reduced_motion_locates_immediately', async () => {
    let displayed: CameraAnchor | null = null
    const motion = new CameraTransition(v => { displayed = v })
    expect(await motion.start(a, b, 100, true, () => true)).toBe(true)
    expect(displayed).toEqual(b); expect(motion.active).toBe(false)
  })
  it('old_animation_cannot_overwrite_new_target_or_door', async () => {
    const frames: CameraAnchor[] = [], doors: number[] = []
    const motion = new CameraTransition(v => frames.push(v))
    let valid = true
    const pending = motion.start(a, b, 0, false, () => valid, p => doors.push(p))
    motion.tick(200); const before = frames.length, doorFrames = doors.length
    valid = false; motion.tick(900)
    expect(await pending).toBe(false); expect(frames.length).toBe(before); expect(doors.length).toBe(doorFrames)
    expect(motion.active).toBe(false)
  })
  it('transition_completes_within_one_second_and_cancel_resolves', async () => {
    let displayed: CameraAnchor | null = null
    const motion = new CameraTransition(v => { displayed = v })
    const complete = motion.start(a, b, 0, false, () => true)
    motion.tick(800); expect(await complete).toBe(true); expect(displayed).toEqual(b)
    const pending = motion.start(b, a, 1000, false, () => true)
    motion.cancel(); expect(await pending).toBe(false); expect(motion.active).toBe(false)
  })
})
