/**
 * Networking conventions:
 * <ul>
 *   <li><b>*Packet</b>: legacy packet wrappers retained for compatibility.</li>
 *   <li><b>*Payload</b>: canonical custom payload transport types for new work.</li>
 * </ul>
 * During cleanup we keep both suffixes where required and migrate call sites incrementally.
 */
package at.koopro.wizardsandbeasts.network;
