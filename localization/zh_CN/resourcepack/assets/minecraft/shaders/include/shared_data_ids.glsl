#ifndef RAVENGARD_SHARED_DATA_IDS_GLSL
#define RAVENGARD_SHARED_DATA_IDS_GLSL

bool shouldApplyEmissives(float sampledColorA) {
    return int(sampledColorA * 255) == 252;
}

bool shouldApplyHalfOpaqueEmissives(float sampledColorA) {
    return int(sampledColorA * 255) == 127;
}

#endif
