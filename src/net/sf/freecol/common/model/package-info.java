/**
 * <p>This package contains the game model, which describes how the
 * individual game objects, such as units, buildings, tiles and so on,
 * interact. The model is extended by the server and used by the client.
 * It should be independent of all packages outside of
 * the <code>common</code> hierarchy.</p>
 * <p>The superclass of most model objects is {@link
 * net.sf.freecol.common.model.FreeColObject FreeColObject}, which
 * provides serialization. The <code>FreeColObject</code> is identified
 * by an ID, which is generally unique. The <code>Feature</code> class
 * and its descendants is a notable exception to this rule,
 * however, since Features with similar effects are provided by
 * various model objects and are grouped by their ID. A fur production
 * bonus, for example, might be granted by a Tile Type, a Unit Type
 * and a Founding Father.</p>
 * <p>The main model objects inherit from the {@link
 * net.sf.freecol.common.model.FreeColGameObject
 * FreeColGameObject}, which contains a reference to the {@link
 * net.sf.freecol.common.model.Game Game}
 * class. Many model objects have a <code>type</code>, which
 * inherits from the {@link
 * net.sf.freecol.common.model.FreeColGameObjectType
 * FreeColGameObjectType}. Units have a UnitType, for
 * example. These Game Object Types are defined by the {@link
 * net.sf.freecol.common.model.Specification Specification}, which
 * is loaded from an XML file when the game starts up.</p>
 */
package net.sf.freecol.common.model;