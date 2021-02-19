// Cylinder
cylinder_height=4;
cylinder_radius=6.5;

// Pinhole
pinhole_diameter=4;

// Grid pit
pit_distance_to_border=0;
angle=75;
slope_len=2.5; //

//Forceps hole
forcep_hole_height = 3;
forcep_hole_bridge_width = 1;
forcep_hole_bridge_length = 3;
forcep_hole_depth = 3;

// Miscellaneous
render_precision=500;

// Modules
module grid_pit() {
	pit_depth = slope_len*cos(angle/2);
	pit_half_width = slope_len*sin(angle/2);
	translate([0, -cylinder_radius + pit_distance_to_border, cylinder_height])
		rotate([0, 90, 90])
			linear_extrude(height=(cylinder_radius-pit_distance_to_border)*2)
				polygon(points=[[0, -pit_half_width], [pit_depth, 0], [0, pit_half_width]]);
}

module pin_hole() {
	translate([cylinder_radius, 0, 0])
		cylinder(d=pinhole_diameter, h=cylinder_height);
}

module forcep_hole() {
	translate([0, 0, cylinder_height-forcep_hole_depth])
		linear_extrude(height=forcep_hole_depth)
			polygon(points=[[-forcep_hole_bridge_length/2, 0], [forcep_hole_bridge_length/2, 0], [0, forcep_hole_height]]);
}

module forcep_holes_neg() {
	translate([0, forcep_hole_bridge_width/2, 0])
		forcep_hole();
	translate([0, -forcep_hole_bridge_width/2, 0])
		rotate([0, 0, 180])
			forcep_hole();
}

// Final putting stuff together
$fn=render_precision;
difference() {
	cylinder(r=cylinder_radius, h=cylinder_height);
	grid_pit();
	pin_hole();
	translate([-(sin(90-angle) * slope_len + cylinder_radius)/2, 0, 0])
		forcep_holes_neg();
}


