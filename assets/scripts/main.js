// 地图尺寸限制
MapResizeDialog.minSize = 5;
MapResizeDialog.maxSize = 1000;
Vars.maxSchematicSize = 600;

// 加载各种脚本模块（注意：这些文件必须存在于 scripts 文件夹里）
require("sectorSize");
require("items");
require("sectors");
require("team2");

// 使 Tantros 星球可见
Planets.tantros.visible = true;

// 因为 Java 代码里的 MultiAssembler 已经在 ShatteredStarMod.java 里注册了
// 如果取消注释 //require("blocks");，你需要有对应的 blocks.js 文件