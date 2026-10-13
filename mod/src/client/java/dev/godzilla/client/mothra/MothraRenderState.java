package dev.godzilla.client.mothra;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** What the renderer needs from Mothra, copied once per frame (interpolated with the partial tick). */
public class MothraRenderState extends EntityRenderState {
	public float yRot;
	public float xRot;
	public float roll;
	/** Wing beat phase in radians, amplitude (radians of stroke), fold (0 spread .. 1 swept back, negative drooping). */
	public float phase;
	public float amp;
	public float fold;
	public float glow;
	public float hurt;
	public int mode;
	public float vx, vy, vz;
	public boolean dying;
}
