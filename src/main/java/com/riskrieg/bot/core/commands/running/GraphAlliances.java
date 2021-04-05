package com.riskrieg.bot.core.commands.running;

import com.mxgraph.layout.*;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.riskrieg.api.Riskrieg;
import com.riskrieg.bot.constant.BotConstants;
import com.riskrieg.bot.core.Command;
import com.riskrieg.bot.core.input.MessageInput;
import com.riskrieg.bot.core.input.SlashInput;
import com.riskrieg.bot.util.ImageUtil;
import com.riskrieg.bot.util.Error;
import com.riskrieg.gamemode.Game;
import com.riskrieg.gamemode.IAlliances;
import com.riskrieg.nation.AllianceNation;
import com.riskrieg.nation.Nation;
import org.jgrapht.Graph;
import org.jgrapht.ext.*;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.AttachmentOption;

public class GraphAlliances extends Command {

  public GraphAlliances() {
    this.settings.setAliases("alliesgraph", "graphallies");
    this.settings.setDescription("Lists all alliances in the game in the form of a graph.");
    this.settings.setEmbedColor(BotConstants.GENERIC_CMD_COLOR);
    this.settings.setGuildOnly(true);
  }

  @Override
  protected void execute(SlashInput input) {

  }

  protected void execute(MessageInput input) {
    Riskrieg api = new Riskrieg();
    Optional<Game> optGame = api.load(input.event().getGuild().getId(), input.event().getChannel().getId());

    if (hasAnyError(optGame, input)) {
      return;
    }

    Graph<String, DefaultEdge> allianceGraph = generateAllyGraph(optGame.get());

    //Create the image from the graph
    JGraphXAdapter<String, DefaultEdge> graphAdapter = stylizeGraph(optGame.get(), allianceGraph);

    mxFastOrganicLayout layout = new mxFastOrganicLayout(graphAdapter);
    layout.setDisableEdgeStyle(false);
    layout.execute(graphAdapter.getDefaultParent());

    BufferedImage image = mxCellRenderer.createBufferedImage(graphAdapter, null, 2, new Color(224, 218, 217) , true, null);
    String filename = "ally-graph";

    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setTitle("Alliances graph");
    embedBuilder.setImage("attachment://" + filename + ".png");
    embedBuilder.setColor(this.settings.getEmbedColor());
    input.event().getChannel().sendMessage(embedBuilder.build()).addFile(ImageUtil.convertToByteArray(image), filename + ".png", new AttachmentOption[0]).queue();
  }

  private Graph<String, DefaultEdge> generateAllyGraph(Game game) {
    Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
    Set<Nation> nationList = game.getNations();
    
    //Create the graph by adding the player names as the vertices and then associating them
    nationList.stream().forEach(nation -> graph.addVertex(game.getPlayer(nation.getLeaderIdentifier().id()).get().getName()));
    for (Nation nation: nationList) {
      Set<Nation> allies = ((AllianceNation) nation).getAllies().stream()
        .map(ally -> game.getNation(ally.id()).get())
        .collect(Collectors.toSet());
      allies.stream().filter(ally -> ((IAlliances) game).allied(ally.getLeaderIdentifier().id(), nation.getLeaderIdentifier().id()))
        .forEach(ally -> graph.addEdge(
          game.getPlayer(nation.getLeaderIdentifier().id()).get().getName(), 
          game.getPlayer(ally.getLeaderIdentifier().id()).get().getName())
      );
    }
    return graph;
  }

  private JGraphXAdapter<String, DefaultEdge> stylizeGraph(Game game, Graph<String, DefaultEdge> graph) {
    JGraphXAdapter<String, DefaultEdge> graphAdapter = new JGraphXAdapter<String, DefaultEdge>(graph);

    //Style vertices
    for (Nation nation: game.getNations()) {
      String name = game.getPlayer(nation.getLeaderIdentifier().id()).get().getName();
      String colour = String.format("%06x", 0xFFFFFF & nation.getLeaderIdentifier().color().value().getRGB());
      graphAdapter.getVertexToCellMap().get(name).setStyle("ellipse;whiteSpace=wrap;html=1;aspect=fixed;strokeWidth=1;noLabel=1;fillColor=#" + colour);

      graphAdapter.getVertexToCellMap().get(name).getGeometry().setWidth(15);
      graphAdapter.getVertexToCellMap().get(name).getGeometry().setHeight(15);
    }

    //Style edges
    for (var cell: graphAdapter.getEdgeToCellMap().values()) {
      cell.setStyle("entryX=0;entryY=0.5;entryDx=0;entryDy=0;startFill=0;endArrow=none;endFill=0;startArrow=none;strokeWidth=1;curved=1;ignoreEdge=0;orthogonal=0;snapToPoint=1;noLabel=1");
    }

    var vertexStyle = graphAdapter.getStylesheet().getDefaultVertexStyle();
    vertexStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
    mxConstants.STYLE_ALIGN = mxConstants.ALIGN_CENTER;
    //If labels in vertices are to be set back on, uncomment this and set noLabel to 0 in style vertices and also increase the width and height
    /*vertexStyle.put(mxConstants.STYLE_LABEL_BACKGROUNDCOLOR, "#e0dad9");
    mxConstants.STYLE_ALIGN = mxConstants.ALIGN_CENTER;
    mxConstants.LABEL_INSET=1;*/ 

     var edgeStyle = graphAdapter.getStylesheet().getDefaultEdgeStyle();
    edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ORTHOGONAL);
    edgeStyle.put(mxConstants.STYLE_ENDARROW, "none");

    graphAdapter.setAllowDanglingEdges(false);

    return graphAdapter;
  }

  private Boolean hasAnyError(Optional<Game> optGame, MessageInput input) {
    if (!optGame.isPresent()) {
      input.event().getChannel().sendMessage(Error.create("You need to create a game before using this command.", this.settings)).queue();
      return true;
    } else if (!(optGame.get() instanceof IAlliances)) {
      input.event().getChannel().sendMessage(Error.create("Invalid game mode.", this.settings)).queue();
      return true;
    } else if (!optGame.get().getGameRule("alliances").isPresent() && !optGame.get().getGameRule("alliances").get().isEnabled()) {
      input.event().getChannel().sendMessage(Error.create("The alliances game rule is disabled.", this.settings)).queue();
      return true;
    } 
    return false;
  }
}
