# Post-correlation cryo-CLEM toolbox

This repository contains utilities to help with the on-lamella post-correlation cryo-CLEM workflow developed in the Chlanda Lab at Heidelberg University.
For more information, see [our paper](https://doi.org/10.1101/2020.02.27.966739).

## FIJI plugin
The FIJI plugin helps with handling the Z-stack tile scan acquired on the Leica cryo-CLEM wide-field microscope.
The files are usually large (>100 GB) and therefore cannot be opened with FIJI or other image processing software.

The StitchTileScan command runs a maximum projection on a tilescan and stitches it to one image.
This is useful for targeted cryo-FIB milling, as the image (exported as a PNG file) can be overlaid with an EM map of the grid using MAPS (ThermoFisher).

The ROIExtractor command allows you to easily extract single Z-stacks from the previously stitched map.
You just need the original tile scan, the ROIset file and the stitched image (both are created by the StitchTileScan command) and and output directory.
The stitched image will be opened and overlaid with the positions of the individual tiles.
Whenever you click into a tile, the corresponding Z-stack will be extracted from the original tile scan and written into the output directory as a .tif.
These are subsequently used for deconvolution and correlation with the matlab script.

## Matlab script
The matlab script performs 3D rigid registration of two Z-stacks, one pre milling (pre-LM map) and one post EM acquisition (post-LM map),
calculating a transformation matrix which is applied to the post-LM map.
