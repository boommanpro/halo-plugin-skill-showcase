package run.halo.skillshowcase;

import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.skillshowcase.extension.Skill;
import run.halo.skillshowcase.extension.SkillCategory;

@Component
public class SkillShowcasePlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public SkillShowcasePlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Skill.class);
        schemeManager.register(SkillCategory.class);
        System.out.println("[skill-showcase] 插件启动成功！");
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Skill.class));
        schemeManager.unregister(Scheme.buildFromType(SkillCategory.class));
        System.out.println("[skill-showcase] 插件停止！");
    }
}