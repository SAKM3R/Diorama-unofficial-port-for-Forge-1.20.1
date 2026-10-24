#version 150
// Before 1.21.6 uniforms were set one by one.
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 maskUv;
out vec4 params;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    maskUv = UV0;
    params = Color;
}
