import { definePlugin } from "@halo-dev/console-shared";
import { markRaw } from "vue";
import SkillListTab from "./views/SkillListTab.vue";

export default definePlugin({
  routes: [],
  extensionPoints: {
    "plugin:self:tabs:create": () => [
      {
        id: "skill-showcase:skill-management",
        label: "Skill 管理",
        component: markRaw(SkillListTab),
      },
    ],
  },
});