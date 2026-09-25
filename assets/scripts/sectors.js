//f
function newPlanet(name, parent, radius) {
	exports[name] = extend(Planet, name, parent, radius, {});
}
function newSector(name, planet, position) {
	exports[name] = extend(SectorPreset, name, planet, position, {});
}

log("ser");
newSector("crash-site", Planets.serpulos, 60);

log("complete");