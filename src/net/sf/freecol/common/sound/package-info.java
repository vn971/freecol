/**
 * This package contains the classes for handling sfx/music in FreeCol.
 * {@link net.sf.freecol.client.FreeColClient} initializes {@link net.sf.freecol.client.control.SoundController} which initializes the players. Pointer to SoundController are stored in FreeColClient and GUI.
 * <p>This is the method for playing sounds (provided you have got access to the pointers):
 * <PRE>
 * soundController.playSound(ILLEGAL_MOVE);
 * </PRE>
 */
package net.sf.freecol.common.sound;