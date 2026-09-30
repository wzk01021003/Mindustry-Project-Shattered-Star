//f
function newPlanet(name, parent, radius) {
	exports[name] = extend(Planet, name, parent, radius, {});
}
function newSector(name, planet, position) {
	exports[name] = extend(SectorPreset, name, planet, position, {});
}

log("sers1");
newSector("crash-site", Planets.tantros, 0);

log("complete");