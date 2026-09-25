function newItem(name) {
	exports[name] = extend(Item, name, {});
}
function newLiquid(name) {
	exports[name] = extend(Liquid, name, {});
}
function newCellLiquid(name) {
	exports[name] = extend(CellLiquid, name, {});
}



newItem("malachite");
newItem("raw-lead");