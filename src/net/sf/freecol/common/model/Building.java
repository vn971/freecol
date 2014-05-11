/**
 *  Copyright (C) 2002-2013   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.Utils;


/**
 * Represents a building in a colony.
 */
public class Building extends WorkLocation implements Named, Comparable<Building>, Consumer {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(Building.class.getName());

    public static final String UNIT_CHANGE = "UNIT_CHANGE";

    /** The type of building. */
    protected BuildingType buildingType;


    /**
     * Constructor for ServerBuilding.
     *
     * @param game The enclosing <code>Game</code>.
     * @param colony The <code>Colony</code> in which this building is located.
     * @param type The <code>BuildingType</code> of building.
     */
    protected Building(Game game, Colony colony, BuildingType type) {
        super(game);

        this.colony = colony;
        this.buildingType = type;
        // set production type to default value
        updateProductionType();
    }

    /**
     * Create a new <code>Building</code> with the given identifier.
     * The object should later be initialized by calling
     * {@link #readFromXML(FreeColXMLReader)}.
     *
     * @param game The enclosing <code>Game</code>.
     * @param id The object identifier.
     */
    public Building(Game game, String id) {
        super(game, id);
    }


    /**
     * Gets the type of this building.
     *
     * @return The building type.
     */
    public BuildingType getType() {
        return buildingType;
    }

    /**
     * Changes the type of the Building.  The type of a building may
     * change when it is upgraded or damaged.
     *
     * -til: If this is a defensive building.
     *
     * @see #upgrade
     * @see #downgrade
     * @param newBuildingType The new <code>BuildingType</code>.
     * @return A list of units present that need to be removed.
     */
    private List<Unit> setType(final BuildingType newBuildingType) {
        // remove features from current type
        Colony colony = getColony();
        colony.removeFeatures(buildingType, "Building change " + buildingType
            + " -> " + newBuildingType + " at " + colony.getName());
        List<Unit> eject = new ArrayList<Unit>();

        if (newBuildingType != null) {
            buildingType = newBuildingType;

            // change default production type
            updateProductionType();

            // add new features and abilities from new type
            colony.addFeatures(buildingType);

            // Colonists which can't work here must be put outside
            for (Unit unit : getUnitList()) {
                if (!canAddType(unit.getType())) eject.add(unit);
            }
        }

        // Colonists exceding units limit must be put outside
        int extra = getUnitCount() - getUnitCapacity() - eject.size();
        for (Unit unit : getUnitList()) {
            if (extra <= 0) break;
            if (!eject.contains(unit)) {
                eject.add(unit);
                extra -= 1;
            }
        }

        return eject;
    }

    /**
     * Gets the level of this building.
     * Delegates to type.
     *
     * @return The building level.
     */
    public int getLevel() {
        return getType().getLevel();
    }

    /**
     * Gets the name of the improved building of the same type.
     * An improved building is a building of a higher level.
     *
     * @return The name of the improved building or <code>null</code> if the
     *     improvement does not exist.
     */
    public String getNextNameKey() {
        final BuildingType next = getType().getUpgradesTo();
        return (next == null) ? null : next.getNameKey();
    }

    /**
     * Does this building have a higher level?
     *
     * @return True if this <code>Building</code> can have a higher level.
     */
    public boolean canBuildNext() {
        return getColony().canBuild(getType().getUpgradesTo());
    }

    /**
     * Can this building can be damaged?
     *
     * @return True if this building can be damaged.
     */
    public boolean canBeDamaged() {
        return !getType().isAutomaticBuild()
            && !getColony().isAutomaticBuild(getType());
    }

    /**
     * Downgrade this building.
     *
     * -til: If this is a defensive building.
     *
     * @return A list of units to eject (usually empty) if the
     *     building was downgraded, or null on failure.
     */
    public List<Unit> downgrade() {
        if (!canBeDamaged()) return null;
        List<Unit> ret = setType(getType().getUpgradesFrom());
        getColony().invalidateCache();
        return ret;
    }

    /**
     * Upgrade this building to next level.
     *
     * -til: If this is a defensive building.
     *
     * @return A list of units to eject (usually empty) if the
     *     building was upgraded, or null on failure.
     */
    public List<Unit> upgrade() {
        if (!canBuildNext()) return null;
        List<Unit> ret = setType(getType().getUpgradesTo());
        getColony().invalidateCache();
        return ret;
    }

    /**
     * Can a particular type of unit be added to this building?
     *
     * @param unitType The <code>UnitType</code> to check.
     * @return True if unit type can be added to this building.
     */
    public boolean canAddType(UnitType unitType) {
        return canBeWorked() && getType().canAdd(unitType);
    }

    /**
     * Convenience function to extract a goods amount from a list of
     * available goods.
     *
     * @param type The <code>GoodsType</code> to extract the amount for.
     * @param available The list of available goods to query.
     * @return The goods amount, or zero if none found.
     */
    private int getAvailable(GoodsType type, List<AbstractGoods> available) {
        for (AbstractGoods goods : available) {
            if (goods.getType() == type) return goods.getAmount();
        }
        return 0;
    }

    /**
     * Gets the production information for this building taking account
     * of the available input and output goods.
     *
     * @param inputs The input goods available.
     * @param outputs The output goods already available in the colony,
     *     necessary in order to avoid excess production.
     * @return The production information.
     * @see ProductionCache#update
     */
    public ProductionInfo getAdjustedProductionInfo(List<AbstractGoods> inputs,
                                                    List<AbstractGoods> outputs) {
        ProductionInfo result = new ProductionInfo();
        if (!hasOutputs()) return result;

        // first, calculate the maximum production

        double minimumRatio = Double.MAX_VALUE;
        double maximumRatio = 0;
        if (canAutoProduce()) {
            for (AbstractGoods output : getOutputs()) {
                GoodsType outputType = output.getType();
                int available = getColony().getGoodsCount(outputType);
                if (available >= outputType.getBreedingNumber()) {
                    // we need at least these many horses/animals to breed
                    double newRatio = 0;
                    int divisor = (int) getType()
                        .applyModifier(0f, Modifier.BREEDING_DIVISOR);
                    if (divisor > 0) {
                        int factor = (int) getType()
                            .applyModifier(0f, Modifier.BREEDING_FACTOR);
                        int maximumOutput = ((available - 1) / divisor + 1) * factor;
                        newRatio = maximumOutput / output.getAmount();
                    }
                    minimumRatio = Math.min(minimumRatio, newRatio);
                    maximumRatio = Math.max(maximumRatio, newRatio);
                } else {
                    minimumRatio = 0;
                }
            }
        } else {
            final Turn turn = getGame().getTurn();
            for (AbstractGoods output : getOutputs()) {
                float production = 0f;
                for (Unit u : getUnitList()) {
                    production += getUnitProduction(u, output.getType());
                }
                List<Modifier> productionModifiers = getProductionModifiers(output.getType(), null);
                production = FeatureContainer
                    .applyModifiers(production, turn, productionModifiers);
                double newRatio = production / output.getAmount();
                minimumRatio = Math.min(minimumRatio, newRatio);
                maximumRatio = Math.max(maximumRatio, newRatio);
            }
        }

        // then, check whether the required inputs are available

        for (AbstractGoods input : getInputs()) {
            int required = (int) (input.getAmount() * minimumRatio);
            int available = getAvailable(input.getType(), inputs);
            // Do not allow auto-production to go negative.
            if (canAutoProduce()) available = Math.max(0, available);
            // experts in factory level buildings may produce a
            // certain amount of goods even when no input is available
            if (available < required
                && hasAbility(Ability.EXPERTS_USE_CONNECTIONS)
                && getSpecification().getBoolean(GameOptions.EXPERTS_HAVE_CONNECTIONS)) {
                int minimumGoodsInput = 0;
                for (Unit unit: getUnitList()) {
                    if (unit.getType() == getExpertUnitType()) {
                        // TODO: put magic number in specification
                        minimumGoodsInput += 4;
                    }
                }
                if (minimumGoodsInput > available) {
                    available = minimumGoodsInput;
                }
            }
            if (available < required) {
                minimumRatio = (minimumRatio * available) / required;
                maximumRatio = Math.max(maximumRatio, minimumRatio);
            }
        }

        // finally, check whether there is space enough to store the
        // goods produced in order to avoid excess production

        if (hasAbility(Ability.AVOID_EXCESS_PRODUCTION)) {
            int capacity = getColony().getWarehouseCapacity();
            for (AbstractGoods output : getOutputs()) {
                double production = (output.getAmount() * minimumRatio);
                if (production > 0) {
                    int amountPresent = getAvailable(output.getType(), outputs);
                    if (production + amountPresent > capacity) {
                        // don't produce more than the warehouse can hold
                        double newRatio = (capacity - amountPresent) / output.getAmount();
                        minimumRatio = Math.min(minimumRatio, newRatio);
                        // and don't claim that more could be produced
                        maximumRatio = minimumRatio;
                    }
                }
            }
        }

        for (AbstractGoods input : getInputs()) {
            GoodsType type = input.getType();
            // maximize consumption
            int consumption = (int) Math.ceil(input.getAmount() * minimumRatio);
            int maximumConsumption = (int) Math.ceil(input.getAmount() * maximumRatio);
            result.addConsumption(new AbstractGoods(type, consumption));
            if (consumption < maximumConsumption) {
                result.addMaximumConsumption(new AbstractGoods(type, maximumConsumption));
            }
        }
        for (AbstractGoods output : getOutputs()) {
            GoodsType type = output.getType();
            // minimize production, but add a magic little something
            // to counter rounding errors
            int production = (int) Math.floor(output.getAmount() * minimumRatio + 0.0001);
            int maximumProduction = (int) Math.floor(output.getAmount() * maximumRatio);
            result.addProduction(new AbstractGoods(type, production));
            if (production < maximumProduction) {
                result.addMaximumProduction(new AbstractGoods(type, maximumProduction));
            }
        }
        return result;
    }


    // Interface Comparable

    /**
     * {@inheritDoc}
     */
    public int compareTo(Building other) {
        return getType().compareTo(other.getType());
    }


    // Interface Location
    // Inherits:
    //   FreeColObject.getId
    //   WorkLocation.getTile
    //   UnitLocation.getLocationNameFor
    //   UnitLocation.contains
    //   UnitLocation.canAdd
    //   WorkLocation.remove
    //   UnitLocation.getUnitCount
    //   final UnitLocation.getUnitIterator
    //   UnitLocation.getUnitList
    //   UnitLocation.getGoodsContainer
    //   final WorkLocation getSettlement
    //   final WorkLocation getColony

    /**
     * {@inheritDoc}
     */
    public StringTemplate getLocationName() {
        return StringTemplate.template("inLocation")
            .add("%location%", getNameKey());
    }


    // Interface UnitLocation
    // Inherits:
    //   UnitLocation.getSpaceTaken
    //   UnitLocation.moveToFront
    //   UnitLocation.clearUnitList
    //   UnitLocation.canBuildEquipment
    //   UnitLocation.canBuildRoleEquipment
    //   UnitLocation.equipForRole
    
    /**
     * {@inheritDoc}
     */
    @Override
    public NoAddReason getNoAddReason(Locatable locatable) {
        if (!(locatable instanceof Unit)) return NoAddReason.WRONG_TYPE;
        NoAddReason reason = getNoWorkReason();

        if (reason == NoAddReason.NONE) {
            reason = getType().getNoAddReason(((Unit) locatable).getType());
            if (reason == NoAddReason.NONE) {
                reason = super.getNoAddReason(locatable);
            }
        }
        return reason;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnitCapacity() {
        return getType().getWorkPlaces();
    }


    // Interface WorkLocation
    // Inherits:
    //   WorkLocation.getBestProductionType(goodsType): moot for buildings.
    //   WorkLocation.getClaimTemplate: buildings do not need to be claimed.

    /**
     * {@inheritDoc}
     */
    public NoAddReason getNoWorkReason() {
        return NoAddReason.NONE;
    }

    /**
     * {@inheritDoc}
     */
    public boolean canAutoProduce() {
        return hasAbility(Ability.AUTO_PRODUCTION);
    }

     /**
      * {@inheritDoc}
      */
    public boolean canProduce(GoodsType goodsType, UnitType unitType) {
        final BuildingType type = getType();
        return type != null && type.canProduce(goodsType, unitType);
    }

    /**
     * {@inheritDoc}
     */
    public int getPotentialProduction(GoodsType goodsType,
                                      UnitType unitType) {
        int amount = (unitType == null) ? 0 : getBaseProduction(goodsType);
        int production = (int)FeatureContainer.applyModifiers(amount,
            getGame().getTurn(),
            getProductionModifiers(goodsType, unitType));
        return Math.max(0, production);
    }

    /**
     * {@inheritDoc}
     */
    public List<Modifier> getProductionModifiers(GoodsType goodsType,
                                                 UnitType unitType) {
        final BuildingType type = getType();
        final String id = (goodsType == null) ? null : goodsType.getId();
        final Colony colony = getColony();
        final Player owner = getOwner();
        final Turn turn = getGame().getTurn();

        List<Modifier> mods = new ArrayList<Modifier>();
        if (unitType == null) { // Add only the building-specific bonuses
            mods.addAll(colony.getModifierSet(id, type, turn));
            if (owner != null) {
                mods.addAll(owner.getModifierSet(id, type, turn));
            }

        } else { // If a unit is present add unit specific bonuses.
            mods.addAll(this.getModifierSet(id, unitType, turn));
            mods.add(colony.getProductionModifier(goodsType));
            mods.addAll(unitType.getModifierSet(id, goodsType, turn));
            if (owner != null) {
                mods.addAll(owner.getModifierSet(id, unitType, turn));
            }
        }
        return mods;
    }

    /**
     * Returns the production types available for this Building.
     *
     * @return available production types
     */
    public List<ProductionType> getProductionTypes() {
        return getType().getProductionTypes(false);
    }


    // Interface Consumer

    /**
     * {@inheritDoc}
     */
    public List<AbstractGoods> getConsumedGoods() {
        return getInputs();
    }

    /**
     * {@inheritDoc}
     */
    public int getPriority() {
        return getType().getPriority();
    }


    // Interface Named

    /**
     * {@inheritDoc}
     */
    public String getNameKey() {
        return getType().getNameKey();
    }


    // Override FreeColObject

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Ability> getAbilitySet(String id, FreeColGameObjectType type,
                                      Turn turn) {
        // Buildings have no abilities independent of their type (for now).
        return getType().getAbilitySet(id, type, turn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Modifier> getModifierSet(String id, FreeColGameObjectType fcgot,
                                        Turn turn) {
        // Buildings have no modifiers independent of type
        return getType().getModifierSet(id, fcgot, turn);
    }


    // Serialization

    private static final String BUILDING_TYPE_TAG = "buildingType";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(BUILDING_TYPE_TAG, buildingType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        final Specification spec = getSpecification();

        buildingType = xr.getType(spec, BUILDING_TYPE_TAG,
                                  BuildingType.class, (BuildingType)null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        sb.append("[").append(getId())
            .append("/").append(getColony().getName())
            .append("]");
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return getXMLElementTagName(); }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "building".
     */
    public static String getXMLElementTagName() {
        return "building";
    }
}
