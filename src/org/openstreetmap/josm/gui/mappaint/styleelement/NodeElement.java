// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Objects;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.data.osm.visitor.paint.StyledMapRenderer;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles.IconReference;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.StyleElementList;
import org.openstreetmap.josm.gui.mappaint.styleelement.BoxTextElement.BoxProvider;
import org.openstreetmap.josm.gui.mappaint.styleelement.BoxTextElement.SimpleBoxProvider;
import org.openstreetmap.josm.gui.util.RotationAngle;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Utils;

/**
 * applies for Nodes and turn restriction relations
 */
public class NodeElement extends StyleElement {
    public final MapImage mapImage;
    public final RotationAngle mapImageAngle;
    public final Symbol symbol;

    public enum SymbolShape { SQUARE, CIRCLE, TRIANGLE, PENTAGON, HEXAGON, HEPTAGON, OCTAGON, NONAGON, DECAGON }

    public static class Symbol {
        public SymbolShape symbol;
        public int size;
        public Stroke stroke;
        public Color strokeColor;
        public Color fillColor;

        public Symbol(SymbolShape symbol, int size, Stroke stroke, Color strokeColor, Color fillColor) {
            if (stroke != null && strokeColor == null)
                throw new IllegalArgumentException("Stroke given without color");
            if (stroke == null && fillColor == null)
                throw new IllegalArgumentException("Either a stroke or a fill color must be given");
            this.symbol = symbol;
            this.size = size;
            this.stroke = stroke;
            this.strokeColor = strokeColor;
            this.fillColor = fillColor;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass())
                return false;
            final Symbol other = (Symbol) obj;
            return symbol == other.symbol &&
                    size == other.size &&
                    Objects.equals(stroke, other.stroke) &&
                    Objects.equals(strokeColor, other.strokeColor) &&
                    Objects.equals(fillColor, other.fillColor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbol, size, stroke, strokeColor, fillColor);
        }

        @Override
        public String toString() {
            return "symbol=" + symbol + " size=" + size +
                    (stroke != null ? " stroke=" + stroke + " strokeColor=" + strokeColor : "") +
                    (fillColor != null ? " fillColor=" + fillColor : "");
        }
    }

    private static final String[] ICON_KEYS = {ICON_IMAGE, ICON_WIDTH, ICON_HEIGHT, ICON_OPACITY, ICON_OFFSET_X, ICON_OFFSET_Y};

    public static final NodeElement SIMPLE_NODE_ELEMSTYLE;
    public static final BoxProvider SIMPLE_NODE_ELEMSTYLE_BOXPROVIDER;
    static {
        MultiCascade mc = new MultiCascade();
        mc.getOrCreateCascade("default");
        SIMPLE_NODE_ELEMSTYLE = create(new Environment(null, mc, "default", null), 4.1f, true);
        if (SIMPLE_NODE_ELEMSTYLE == null) throw new AssertionError();
        SIMPLE_NODE_ELEMSTYLE_BOXPROVIDER = SIMPLE_NODE_ELEMSTYLE.getBoxProvider();
    }

    public static final StyleElementList DEFAULT_NODE_STYLELIST = new StyleElementList(NodeElement.SIMPLE_NODE_ELEMSTYLE);
    public static final StyleElementList DEFAULT_NODE_STYLELIST_TEXT = new StyleElementList(NodeElement.SIMPLE_NODE_ELEMSTYLE,
            BoxTextElement.SIMPLE_NODE_TEXT_ELEMSTYLE);

    protected NodeElement(Cascade c, MapImage mapImage, Symbol symbol, float defaultMajorZindex, RotationAngle rotationAngle) {
        super(c, defaultMajorZindex);
        this.mapImage = mapImage;
        this.symbol = symbol;
        this.mapImageAngle = rotationAngle;
    }

    public static NodeElement create(Environment env) {
        return create(env, 4f, false);
    }

    private static NodeElement create(Environment env, float defaultMajorZindex, boolean allowDefault) {
        Cascade c = env.mc.getCascade(env.layer);

        MapImage mapImage = createIcon(env, ICON_KEYS);
        Symbol symbol = null;
        if (mapImage == null) {
            symbol = createSymbol(env);
        }
        RotationAngle rotationAngle = null;
        final Float angle = c.get(ICON_ROTATION, null, Float.class, true);
        if (angle != null) {
            rotationAngle = RotationAngle.buildStaticRotation(angle);
        } else {
            final Keyword rotationKW = c.get(ICON_ROTATION, null, Keyword.class);
            if (rotationKW != null) {
                if ("way".equals(rotationKW.val)) {
                    rotationAngle = RotationAngle.buildWayDirectionRotation();
                } else {
                    try {
                        rotationAngle = RotationAngle.buildStaticRotation(rotationKW.val);
                    } catch (IllegalArgumentException ignore) {
                        Main.trace(ignore);
                    }
                }
            }
        }

        // optimization: if we neither have a symbol, nor a mapImage
        // we don't have to check for the remaining style properties and we don't
        // have to allocate a node element style.
        if (!allowDefault && symbol == null && mapImage == null) return null;

        return new NodeElement(c, mapImage, symbol, defaultMajorZindex, rotationAngle);
    }

    public static MapImage createIcon(final Environment env, final String[] keys) {
        CheckParameterUtil.ensureParameterNotNull(env, "env");
        CheckParameterUtil.ensureParameterNotNull(keys, "keys");

        Cascade c = env.mc.getCascade(env.layer);

        final IconReference iconRef = c.get(keys[ICON_IMAGE_IDX], null, IconReference.class, true);
        if (iconRef == null)
            return null;

        Cascade cDef = env.mc.getCascade("default");

        Float widthOnDefault = cDef.get(keys[ICON_WIDTH_IDX], null, Float.class);
        if (widthOnDefault != null && widthOnDefault <= 0) {
            widthOnDefault = null;
        }
        Float widthF = getWidth(c, keys[ICON_WIDTH_IDX], widthOnDefault);

        Float heightOnDefault = cDef.get(keys[ICON_HEIGHT_IDX], null, Float.class);
        if (heightOnDefault != null && heightOnDefault <= 0) {
            heightOnDefault = null;
        }
        Float heightF = getWidth(c, keys[ICON_HEIGHT_IDX], heightOnDefault);

        int width = widthF == null ? -1 : Math.round(widthF);
        int height = heightF == null ? -1 : Math.round(heightF);

        float offsetXF = 0f;
        float offsetYF = 0f;
        if (keys[ICON_OFFSET_X_IDX] != null) {
            offsetXF = c.get(keys[ICON_OFFSET_X_IDX], 0f, Float.class);
            offsetYF = c.get(keys[ICON_OFFSET_Y_IDX], 0f, Float.class);
        }

        final MapImage mapImage = new MapImage(iconRef.iconName, iconRef.source);

        mapImage.width = width;
        mapImage.height = height;
        mapImage.offsetX = Math.round(offsetXF);
        mapImage.offsetY = Math.round(offsetYF);

        mapImage.alpha = Math.min(255, Math.max(0, Integer.valueOf(Main.pref.getInteger("mappaint.icon-image-alpha", 255))));
        Integer pAlpha = Utils.color_float2int(c.get(keys[ICON_OPACITY_IDX], null, float.class));
        if (pAlpha != null) {
            mapImage.alpha = pAlpha;
        }
        return mapImage;
    }

    private static Symbol createSymbol(Environment env) {
        Cascade c = env.mc.getCascade(env.layer);
        Cascade cDef = env.mc.getCascade("default");

        SymbolShape shape;
        Keyword shapeKW = c.get("symbol-shape", null, Keyword.class);
        if (shapeKW == null)
            return null;
        if ("square".equals(shapeKW.val)) {
            shape = SymbolShape.SQUARE;
        } else if ("circle".equals(shapeKW.val)) {
            shape = SymbolShape.CIRCLE;
        } else if ("triangle".equals(shapeKW.val)) {
            shape = SymbolShape.TRIANGLE;
        } else if ("pentagon".equals(shapeKW.val)) {
            shape = SymbolShape.PENTAGON;
        } else if ("hexagon".equals(shapeKW.val)) {
            shape = SymbolShape.HEXAGON;
        } else if ("heptagon".equals(shapeKW.val)) {
            shape = SymbolShape.HEPTAGON;
        } else if ("octagon".equals(shapeKW.val)) {
            shape = SymbolShape.OCTAGON;
        } else if ("nonagon".equals(shapeKW.val)) {
            shape = SymbolShape.NONAGON;
        } else if ("decagon".equals(shapeKW.val)) {
            shape = SymbolShape.DECAGON;
        } else
            return null;

        Float sizeOnDefault = cDef.get("symbol-size", null, Float.class);
        if (sizeOnDefault != null && sizeOnDefault <= 0) {
            sizeOnDefault = null;
        }
        Float size = getWidth(c, "symbol-size", sizeOnDefault);

        if (size == null) {
            size = 10f;
        }

        if (size <= 0)
            return null;

        Float strokeWidthOnDefault = getWidth(cDef, "symbol-stroke-width", null);
        Float strokeWidth = getWidth(c, "symbol-stroke-width", strokeWidthOnDefault);

        Color strokeColor = c.get("symbol-stroke-color", null, Color.class);

        if (strokeWidth == null && strokeColor != null) {
            strokeWidth = 1f;
        } else if (strokeWidth != null && strokeColor == null) {
            strokeColor = Color.ORANGE;
        }

        Stroke stroke = null;
        if (strokeColor != null && strokeWidth != null) {
            Integer strokeAlpha = Utils.color_float2int(c.get("symbol-stroke-opacity", null, Float.class));
            if (strokeAlpha != null) {
                strokeColor = new Color(strokeColor.getRed(), strokeColor.getGreen(),
                        strokeColor.getBlue(), strokeAlpha);
            }
            stroke = new BasicStroke(strokeWidth);
        }

        Color fillColor = c.get("symbol-fill-color", null, Color.class);
        if (stroke == null && fillColor == null) {
            fillColor = Color.BLUE;
        }

        if (fillColor != null) {
            Integer fillAlpha = Utils.color_float2int(c.get("symbol-fill-opacity", null, Float.class));
            if (fillAlpha != null) {
                fillColor = new Color(fillColor.getRed(), fillColor.getGreen(),
                        fillColor.getBlue(), fillAlpha);
            }
        }

        return new Symbol(shape, Math.round(size), stroke, strokeColor, fillColor);
    }

    @Override
    public void paintPrimitive(OsmPrimitive primitive, MapPaintSettings settings, StyledMapRenderer painter,
            boolean selected, boolean outermember, boolean member) {
        if (primitive instanceof Node) {
            Node n = (Node) primitive;
            if (mapImage != null && painter.isShowIcons()) {
                painter.drawNodeIcon(n, mapImage, painter.isInactiveMode() || n.isDisabled(), selected, member,
                        mapImageAngle == null ? 0.0 : mapImageAngle.getRotationAngle(primitive));
            } else if (symbol != null) {
                Color fillColor = symbol.fillColor;
                if (fillColor != null) {
                    if (painter.isInactiveMode() || n.isDisabled()) {
                        fillColor = settings.getInactiveColor();
                    } else if (defaultSelectedHandling && selected) {
                        fillColor = settings.getSelectedColor(fillColor.getAlpha());
                    } else if (member) {
                        fillColor = settings.getRelationSelectedColor(fillColor.getAlpha());
                    }
                }
                Color strokeColor = symbol.strokeColor;
                if (strokeColor != null) {
                    if (painter.isInactiveMode() || n.isDisabled()) {
                        strokeColor = settings.getInactiveColor();
                    } else if (defaultSelectedHandling && selected) {
                        strokeColor = settings.getSelectedColor(strokeColor.getAlpha());
                    } else if (member) {
                        strokeColor = settings.getRelationSelectedColor(strokeColor.getAlpha());
                    }
                }
                painter.drawNodeSymbol(n, symbol, fillColor, strokeColor);
            } else {
                Color color;
                boolean isConnection = n.isConnectionNode();

                if (painter.isInactiveMode() || n.isDisabled()) {
                    color = settings.getInactiveColor();
                } else if (selected) {
                    color = settings.getSelectedColor();
                } else if (member) {
                    color = settings.getRelationSelectedColor();
                } else if (isConnection) {
                    if (n.isTagged()) {
                        color = settings.getTaggedConnectionColor();
                    } else {
                        color = settings.getConnectionColor();
                    }
                } else {
                    if (n.isTagged()) {
                        color = settings.getTaggedColor();
                    } else {
                        color = settings.getNodeColor();
                    }
                }

                final int size = Utils.max(selected ? settings.getSelectedNodeSize() : 0,
                        n.isTagged() ? settings.getTaggedNodeSize() : 0,
                        isConnection ? settings.getConnectionNodeSize() : 0,
                        settings.getUnselectedNodeSize());

                final boolean fill = (selected && settings.isFillSelectedNode()) ||
                (n.isTagged() && settings.isFillTaggedNode()) ||
                (isConnection && settings.isFillConnectionNode()) ||
                settings.isFillUnselectedNode();

                painter.drawNode(n, color, size, fill);

            }
        } else if (primitive instanceof Relation && mapImage != null) {
            painter.drawRestriction((Relation) primitive, mapImage, painter.isInactiveMode() || primitive.isDisabled());
        }
    }

    public BoxProvider getBoxProvider() {
        if (mapImage != null)
            return mapImage.getBoxProvider();
        else if (symbol != null)
            return new SimpleBoxProvider(new Rectangle(-symbol.size/2, -symbol.size/2, symbol.size, symbol.size));
        else {
            // This is only executed once, so no performance concerns.
            // However, it would be better, if the settings could be changed at runtime.
            int size = Utils.max(
                    Main.pref.getInteger("mappaint.node.selected-size", 5),
                    Main.pref.getInteger("mappaint.node.unselected-size", 3),
                    Main.pref.getInteger("mappaint.node.connection-size", 5),
                    Main.pref.getInteger("mappaint.node.tagged-size", 3)
            );
            return new SimpleBoxProvider(new Rectangle(-size/2, -size/2, size, size));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mapImage, mapImageAngle, symbol);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;
        NodeElement that = (NodeElement) obj;
        return Objects.equals(mapImage, that.mapImage) &&
               Objects.equals(mapImageAngle, that.mapImageAngle) &&
               Objects.equals(symbol, that.symbol);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(64).append("NodeElemStyle{").append(super.toString());
        if (mapImage != null) {
            s.append(" icon=[" + mapImage + ']');
        }
        if (symbol != null) {
            s.append(" symbol=[" + symbol + ']');
        }
        if (mapImageAngle != null) {
            s.append(" mapImageAngle=[" + mapImageAngle + ']');
        }
        s.append('}');
        return s.toString();
    }
}
