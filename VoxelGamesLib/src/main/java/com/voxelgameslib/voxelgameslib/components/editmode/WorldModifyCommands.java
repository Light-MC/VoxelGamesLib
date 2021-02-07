package com.voxelgameslib.voxelgameslib.components.editmode;

import com.google.inject.Singleton;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import java.io.File;
import java.util.Optional;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.voxelgameslib.voxelgameslib.api.exception.WorldException;
import com.voxelgameslib.voxelgameslib.api.feature.features.MapFeature;
import com.voxelgameslib.voxelgameslib.api.feature.features.SpawnFeature;
import com.voxelgameslib.voxelgameslib.api.game.Game;
import com.voxelgameslib.voxelgameslib.api.game.GameHandler;
import com.voxelgameslib.voxelgameslib.components.map.Map;
import com.voxelgameslib.voxelgameslib.components.user.User;
import com.voxelgameslib.voxelgameslib.components.world.WorldHandler;
import com.voxelgameslib.voxelgameslib.internal.lang.Lang;
import com.voxelgameslib.voxelgameslib.internal.lang.LangKey;
import com.voxelgameslib.voxelgameslib.internal.math.Vector3D;
import com.voxelgameslib.voxelgameslib.util.utils.FileUtils;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;

@Singleton
@CommandAlias("worldmodifier|wm")
public class WorldModifyCommands extends BaseCommand {

    private static final Logger log = Logger.getLogger(WorldModifyCommands.class.getName());
    @Inject
    private WorldHandler worldHandler;
    @Inject
    private GameHandler gameHandler;

    private User editor;
    private Game game;
    private Map map;

    @HelpCommand
    @CommandPermission("%admin")
    public void doHelp(@Nonnull User sender, @Nonnull CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("start")
    @CommandPermission("%admin")
    @Syntax("<world> - the name of the map you want to modify")
    public void start(@Nonnull User user, @Nonnull String world) {
        if (editor != null) {
            Lang.msg(user, LangKey.WORLD_CREATOR_IN_USE,
                    editor.getDisplayName());
            return;
        }
        editor = user;

        // load data
        Optional<Map> o = worldHandler.getMap(world);
        this.map = o.orElseGet(() -> worldHandler.loadMap(world));

        // load world
        map.load(editor.getUuid(), map.getWorldName());

        File file = new File(worldHandler.getWorldContainer(), map.getLoadedName(editor.getUuid()));

        try {
            ZipFile zip = new ZipFile(new File(worldHandler.getWorldsFolder(), map.getWorldName() + ".zip"));
            zip.extractAll(file.getAbsolutePath());
            FileUtils.delete(new File(file, "uid.dat"));
        } catch (ZipException e) {
            throw new WorldException("Could not unzip world " + map.getInfo().getDisplayName() + "(" + map.getWorldName() + ".zip).", e);
        }

        worldHandler.loadLocalWorld(map.getLoadedName(editor.getUuid()));

        // tp
        user.getPlayer().teleportAsync(map.getCenter().toLocation(map.getLoadedName(user.getUuid())));

        if (gameHandler.getDefaultGame().isParticipating(editor.getUuid())) {
            gameHandler.getDefaultGame().leave(editor, false);
        }
        game = gameHandler.startGame(EditModeGame.GAMEMODE);
        game.getActivePhase().getNextPhase().getFeature(SpawnFeature.class).addSpawn(map.getCenter());
        game.getActivePhase().getNextPhase().getFeature(MapFeature.class).setMap(map);
        map.load(game.getUuid(), map.getWorldName());
        game.join(editor);
        game.endPhase();

        map.getInfo().setWorldName(map.getWorldName());
        Lang.msg(user, LangKey.WORLD_MODIFY_START);
        //TODO use inventory for world creator
    }

    @Subcommand("displayname")
    @CommandPermission("%admin")
    @Syntax("[name] - the new display name of the map")
    public void displayname(@Nonnull User user, @Nullable @co.aikar.commands.annotation.Optional String name) {
        if (!check(user)) return;
        // view
        if (name == null) {
            Lang.msg(user, LangKey.WORLD_MODIFY_DISPLAYNAME_VIEW, map.getInfo().getDisplayName());
        }
        // edit
        else {
            map.getInfo().setDisplayName(name);
            Lang.msg(user, LangKey.WORLD_MODIFY_DISPLAYNAME_EDIT, name);
        }
    }

    @Subcommand("author")
    @CommandPermission("%admin")
    @Syntax("[author] - the new author of the map")
    public void author(@Nonnull User user, @Nullable @co.aikar.commands.annotation.Optional String author) {
        if (!check(user)) return;
        // view
        if (author == null) {
            Lang.msg(user, LangKey.WORLD_MODIFY_AUTHOR_VIEW, map.getInfo().getAuthor());
        }
        // edit
        else {
            map.getInfo().setAuthor(author);
            Lang.msg(user, LangKey.WORLD_MODIFY_AUTHOR_EDIT, author);
        }
    }

    @Subcommand("radius")
    @CommandPermission("%admin")
    @Syntax("<radius> - the radius of the map")
    public void radius(@Nonnull User user, @Nullable @co.aikar.commands.annotation.Optional Integer radius) {
        if (!check(user)) return;
        // view
        if (radius == null) {
            Lang.msg(user, LangKey.WORLD_MODIFY_RADIUS_VIEW, map.getRadius());
        }
        // edit
        else {
            map.setRadius(radius);
            Lang.msg(user, LangKey.WORLD_MODIFY_RADIUS_EDIT, radius);
        }
    }

    @Subcommand("center")
    @CommandPermission("%admin")
    public void center(@Nonnull User user, @Nullable @co.aikar.commands.annotation.Optional String set) {
        if (!check(user)) return;
        // view
        if (set == null) {
            Lang.msg(user, LangKey.WORLD_MODIFY_CENTER_VIEW, map.getCenter());
        }
        // edit
        else {
            map.setCenter(new Vector3D(user.getPlayer().getLocation().getX(), user.getPlayer().getLocation().getY(), user.getPlayer().getLocation().getZ()));
            Lang.msg(user, LangKey.WORLD_MODIFY_CENTER_EDIT, map.getCenter());
        }
    }

    @Subcommand("gamemode")
    @CommandPermission("%admin")
    @Syntax("<gamemode> - the gamesmodes")
    public void gamemode(@Nonnull User user, @Nullable @co.aikar.commands.annotation.Optional String gamemode) {
        if (!check(user)) return;
        // remove all
        // add one
        // save
        //TODO lets do this later, we need a nice inventory here
    }

    @Subcommand("stop")
    @CommandPermission("%admin")
    public void stop(@Nonnull User user) {
        if (!check(user)) return;
        worldHandler.finishWorldEditing(editor, map);

        game.abortGame();
        if (gameHandler.getDefaultGame() != null) {
            gameHandler.getDefaultGame().join(editor);
        }
        game = null;

        editor = null;
        map = null;
    }

    private boolean check(@Nonnull User user) {
        if (editor == null) {
            Lang.msg(user, LangKey.WORLD_MODIFY_NOT_STARTED);
            return false;
        }
        if (!editor.getUuid().equals(user.getUuid())) {
            Lang.msg(user, LangKey.WORLD_CREATOR_IN_USE, editor.getDisplayName());
            return false;
        }
        return true;
    }
}
