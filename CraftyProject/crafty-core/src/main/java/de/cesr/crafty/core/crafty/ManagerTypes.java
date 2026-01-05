package de.cesr.crafty.core.crafty;

/**
 * Enumerates the high-level manager types used to classify cell owners.
 *
 * - {@link #AFT}: an active agent functional type that can compete and manage land.
 * - {@link #Abandoned}: unmanaged land state (no active management; may be eligible for takeover).
 * - {@link #MASK}: a non-competing “owner” used to represent spatial masks / restrictions
 *   (e.g., protected areas) that constrain allowed transitions.
 *
 * These types are used throughout the model to quickly distinguish interacting managers from
 * non-interacting placeholders during competition, abandonment, and masking logic.
 */
/**
 * @author Mohamed Byari
 *
 */
public enum ManagerTypes {
	MASK, AFT, Abandoned

}
