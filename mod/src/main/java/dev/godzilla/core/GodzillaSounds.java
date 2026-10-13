package dev.godzilla.core;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events, one per key of assets/godzilla/sounds.json (written by audio/ into the jar): the key "godzilla.roar" is the
 * event godzilla:godzilla.roar. Durations, loop lengths in ticks and music bar times are in audio/out/manifest.json.
 * Minecraft cannot loop an event: replay the *_LOOP ones every manifest "ticks".
 * Server code plays them with level.playSound(null, pos, EVENT, SoundSource.X, vol, pitch);
 * on a test client: /playsound godzilla:godzilla.roar master @s.
 */
public final class GodzillaSounds {
	public static final SoundEvent BREATH_CHARGE = register("breath.charge");
	public static final SoundEvent BREATH_END = register("breath.end");
	public static final SoundEvent BREATH_IMPACT_LOOP = register("breath.impact_loop");
	public static final SoundEvent BREATH_LOOP = register("breath.loop");
	public static final SoundEvent CAR_ALARM = register("car.alarm");
	public static final SoundEvent CAR_HORN = register("car.horn");
	public static final SoundEvent CAR_SKID = register("car.skid");
	public static final SoundEvent COLLAPSE_RUMBLE = register("collapse.rumble");
	public static final SoundEvent COLLAPSE_SETTLE = register("collapse.settle");
	public static final SoundEvent EXPLOSION_CAR = register("explosion.car");
	public static final SoundEvent EXPLOSION_FUEL = register("explosion.fuel");
	public static final SoundEvent EXPLOSION_LARGE = register("explosion.large");
	public static final SoundEvent FIRE_LOOP = register("fire.loop");
	public static final SoundEvent GODZILLA_DEATH = register("godzilla.death");
	public static final SoundEvent GODZILLA_FOOTSTEP = register("godzilla.footstep");
	public static final SoundEvent GODZILLA_HAND_SWOOSH = register("godzilla.hand_swoosh");
	public static final SoundEvent GODZILLA_HURT = register("godzilla.hurt");
	public static final SoundEvent GODZILLA_ROAR = register("godzilla.roar");
	public static final SoundEvent GODZILLA_ROAR_SHORT = register("godzilla.roar_short");
	public static final SoundEvent GODZILLA_SPLASH_STEP = register("godzilla.splash_step");
	public static final SoundEvent GODZILLA_SURFACE = register("godzilla.surface");
	public static final SoundEvent GODZILLA_TAIL_SWOOSH = register("godzilla.tail_swoosh");
	public static final SoundEvent GODZILLA_UNDERWATER_LOOP = register("godzilla.underwater_loop");
	public static final SoundEvent MIL_HELI_LOOP = register("mil.heli_loop");
	public static final SoundEvent MIL_JET_PASS = register("mil.jet_pass");
	public static final SoundEvent MIL_MASER_CHARGE = register("mil.maser_charge");
	public static final SoundEvent MIL_MASER_LOOP = register("mil.maser_loop");
	public static final SoundEvent MIL_MG = register("mil.mg");
	public static final SoundEvent MIL_MISSILE = register("mil.missile");
	public static final SoundEvent MIL_SHELL_HIT = register("mil.shell_hit");
	public static final SoundEvent MIL_SHIP_CANNON = register("mil.ship_cannon");
	public static final SoundEvent MIL_TANK_ENGINE_LOOP = register("mil.tank_engine_loop");
	public static final SoundEvent MIL_TANK_FIRE = register("mil.tank_fire");
	public static final SoundEvent MOTHRA_CRY = register("mothra.cry");
	public static final SoundEvent MOTHRA_DEATH = register("mothra.death");
	public static final SoundEvent MOTHRA_DIVE = register("mothra.dive");
	public static final SoundEvent MOTHRA_GUST = register("mothra.gust");
	public static final SoundEvent MOTHRA_HURT = register("mothra.hurt");
	public static final SoundEvent MOTHRA_SCALES = register("mothra.scales");
	public static final SoundEvent MOTHRA_WING_LOOP = register("mothra.wing_loop");
	public static final SoundEvent MUSIC_DEFEAT = register("music.defeat");
	public static final SoundEvent MUSIC_MOTHRA_ARRIVAL = register("music.mothra_arrival");
	public static final SoundEvent MUSIC_MOTHRA_FIGHT = register("music.mothra_fight");
	public static final SoundEvent MUSIC_RAMPAGE = register("music.rampage");
	public static final SoundEvent MUSIC_RAMPAGE_INTENSE = register("music.rampage_intense");
	public static final SoundEvent MUSIC_RESULTS_LOOP = register("music.results_loop");
	public static final SoundEvent MUSIC_RISE = register("music.rise");
	public static final SoundEvent MUSIC_VICTORY = register("music.victory");
	public static final SoundEvent PEOPLE_CROWD_PANIC_LOOP = register("people.crowd_panic_loop");
	public static final SoundEvent PEOPLE_SCREAM = register("people.scream");
	public static final SoundEvent PEOPLE_SQUISH = register("people.squish");
	public static final SoundEvent RUBBLE_HIT = register("rubble.hit");
	public static final SoundEvent SIREN_CITY_LOOP = register("siren.city_loop");
	public static final SoundEvent SIREN_POLICE = register("siren.police");
	public static final SoundEvent SMASH_GLASS = register("smash.glass");
	public static final SoundEvent SMASH_LARGE = register("smash.large");
	public static final SoundEvent SMASH_MEDIUM = register("smash.medium");
	public static final SoundEvent SMASH_SMALL = register("smash.small");
	public static final SoundEvent TRAIN_PASS = register("train.pass");
	public static final SoundEvent UI_BIG_BONUS = register("ui.big_bonus");
	public static final SoundEvent UI_COMBO_BREAK = register("ui.combo_break");
	public static final SoundEvent UI_COMBO_UP = register("ui.combo_up");
	public static final SoundEvent UI_GAME_OVER = register("ui.game_over");
	public static final SoundEvent UI_SCORE_TICK = register("ui.score_tick");
	public static final SoundEvent UI_TITLE_HIT = register("ui.title_hit");
	public static final SoundEvent UI_VICTORY_STING = register("ui.victory_sting");

	/** Same as GODZILLA_ROAR. */
	public static final SoundEvent ROAR = GODZILLA_ROAR;

	private GodzillaSounds() {
	}

	private static SoundEvent register(String id) {
		// createVariableRangeEvent: audible range depends on the volume argument. createFixedRangeEvent(id, blocks) pins it.
		return Registry.register(BuiltInRegistries.SOUND_EVENT, GodzillaMod.id(id), SoundEvent.createVariableRangeEvent(GodzillaMod.id(id)));
	}

	public static void register() {
	}
}