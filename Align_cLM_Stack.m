%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% Script by Steffen Klein & Benedikt Wimmer
% Copyright Chlanda Lab Heidelberg

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% Calculate rigid 3D transformation matrix between two Z-stacks.
% Both files should be 16-bit composite TIF images created in FIJI/ImageJ and have the same size.
% The results are better if both stacks are already roughly manually aligned using FIJI/ImageJ.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

clear;
clc;
close all;
tic;

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% SET ALL NECCESARY VARIABLES HERE

% Set filenames of fixed and moving image
% image_before_milling: path to the image stack before milling / EM acquisition -> fixed image
image_before_milling = 'stack_after_milling.tif';
% image_after_milling: path to the image stack after milling / EM acquisition -> moving image
image_after_milling = 'stack_before_milling.tif';

% Set channel information
% channels: total number of channels in tif file
channels = 3;
% channel_for_alignment: number of channel that should be used for registration (one-indexed)
% small fiducial markers like lipiblue work best
channel_for_alignment = 3;

% Set pixel size of files in microns
% xy_resUM: pixel size in X/Y
xy_resUM = 0.13;
% z_resUM: pixel size in Z
z_resUM = 0.3;

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% DO NOT CHANGE ANYTHING BEYOND THIS POINT
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Extract image dimensions from imported files
[size_X_fixed, size_Y_fixed] = size(imread(image_before_milling));
size_Z_fixed = (size(imfinfo(image_before_milling), 1) / channels);
size_fixed = [size_X_fixed, size_Y_fixed, size_Z_fixed];
clear size_X_fixed size_Y_fixed size_Z_fixed;

size_Z_moving = (size(imfinfo(image_after_milling), 1) / channels);
[size_X_moving, size_Y_moving] = size(imread(image_after_milling));
size_moving = [size_X_moving, size_Y_moving, size_Z_moving];
clear size_X_moving size_Y_moving size_Z_moving;

% Create 3D reference object which holds dimension information for both
% images
fixed_reference = imref3d(size_fixed, xy_resUM, xy_resUM, z_resUM);
moving_reference = imref3d(size_moving, xy_resUM, xy_resUM, z_resUM);

% Read multichannel tif image_after into fixed_image cell array
fixed_image = init_cell(channels, size_fixed(1), size_fixed(2), size_fixed(3));

k = 1;
for i = 1:size_fixed(3)
    for j = 1:channels
        fixed_image{j}(:, :, i) = imread(image_before_milling, k);
        k = k + 1;
    end
end

% Read multichannel tif of image_before into moving_image cell array
moving_image = init_cell(channels, size_moving(1), size_moving(2), size_moving(3));

k = 1;
for i = 1:size_moving(3)
    for j = 1:channels
        moving_image{j}(:, :, i) = imread(image_after_milling, k);
        k = k + 1;
    end
end

disp('finished importing images');
toc;

% Use imregtform to calculate transformation matrix based on the fiducial
% map using translation and rotation only.

% Setup parameters
optimizer = registration.optimizer.RegularStepGradientDescent;
optimizer.GradientMagnitudeTolerance = 1e-5;
optimizer.MinimumStepLength = 5e-6;
optimizer.MaximumStepLength = 0.05;
optimizer.MaximumIterations = 5000;
optimizer.RelaxationFactor = 0.6;
metric = registration.metric.MattesMutualInformation;

% calculate transformation matrix
transformation_matrix = imregtform(moving_image{channel_for_alignment}, moving_reference, fixed_image{channel_for_alignment}, fixed_reference, 'rigid', optimizer, metric, 'DisplayOptimization', true);

disp('finished calculating transformation matrix');
toc;

% Transform all channels of moving_image, clear untransformed stack
% from memory

moving_image_transformed = init_cell(channels, size_fixed(1), size_fixed(2), size_fixed(3));

for i = 1:channels
    [moving_image_transformed{i}, ~] = imwarp(moving_image{i}, moving_reference, transformation_matrix, 'OutputView', fixed_reference);
end

clear moving_image new_ref;

disp('finished transforming image stack');
toc;

% Save transformation_matrix
csvwrite('transformation_matrix.csv', transformation_matrix.T);

% Save transformed and fixed stack as one greyscale TIF per channel.

export_stack(fixed_image);
export_stack(moving_image_transformed);

disp('export complete');
toc;

% Functions down here: init_cell initializes a cell array of specified
% dimensions, export_stack writes tif stacks which work with FIJI.
function c = init_cell(ch, sizeX, sizeY, sizeZ)
    c = cell(1, ch);
    c(:) = {zeros(sizeX, sizeY, sizeZ, 'uint16')};
end

function export_stack(image)
     channels = size(image, 2);
     stack = size(image{1}, 3);
    for i = 1:channels
        export_filename = strcat(inputname(1), '_ch_', int2str(i), '.tif');
        imwrite(image{i}(:, :, 1), export_filename, 'Compression', 'lzw');
    end
    for j = 2:stack
        imwrite(image{i}(:, :, j), export_filename, 'WriteMode', 'append', 'Compression', 'lzw');
    end
end