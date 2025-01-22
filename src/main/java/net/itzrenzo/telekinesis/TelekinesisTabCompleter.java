package net.itzrenzo.telekinesis;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TelekinesisTabCompleter implements TabCompleter {

    private static final List<String> FIRST_ARGUMENTS = Arrays.asList("on", "off", "blacklist", "reload");
    private static final List<String> BLACKLIST_ACTIONS = Arrays.asList("add", "remove", "list");
    private static final List<String> MATERIAL_NAMES;

    static {
        MATERIAL_NAMES = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(material -> material.name().toLowerCase())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], FIRST_ARGUMENTS, completions);
        } else if (args.length == 2 && "blacklist".equalsIgnoreCase(args[0])) {
            StringUtil.copyPartialMatches(args[1], BLACKLIST_ACTIONS, completions);
        } else if (args.length == 3 && "blacklist".equalsIgnoreCase(args[0])
                && ("add".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))) {
            StringUtil.copyPartialMatches(args[2], MATERIAL_NAMES, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}