# Third-party notices

ETKN TV is distributed under GPL-3.0 (see `LICENSE`). Original third-party
copyright notices and licenses remain applicable to their respective files.
Full license texts are bundled in the APK under `assets/licenses` and can be
read in Settings → 开源许可.

| Component | Pinned version/source | License |
| --- | --- | --- |
| AndroidX Media3 / ExoPlayer | 1.8.0, androidx/media | Apache-2.0 |
| AndroidX Core | 1.16.0 | Apache-2.0 |
| Kotlin runtime | 2.2.0 | Apache-2.0 |
| NextLib Media3 extension | 1.8.0-0.9.0, anilbeesetti/nextlib | GPL-3.0 |
| FFmpeg | 6.0, original NextLib binaries | LGPL-3.0 configuration; see FFmpeg LICENSE |
| libvpx | 1.13.0 | BSD-style |
| mbedTLS | 3.4.1 | Apache-2.0 / GPL-2.0-or-later, upstream dual license |
| libass Android wrapper and libass-cmake | v0.5.1 and d3f00a43ca66e42a2c34de964b1a7dbbfa9dbc8b | MIT |
| libass | bbb3c7f1570a4a021e52683f3fbdf74fe492ae84 | ISC |
| FreeType | 42608f77f20749dd6ddc9e0536788eaad70ea4b5 | FreeType License |
| FriBidi | 68162babff4f39c4e2dc164a5e825af93bda9983 | LGPL-2.1-or-later |
| HarfBuzz | c3fcbffa651cea70400552f2a8bd695ad11023c1 | MIT-style |
| Fontconfig | daa175d234b8a362eedd4c18c33537cc2d19cd98 | MIT-style |
| Expat | f9a3eeb3e09fbea04b1c451ffc422ab2f1e45744 | MIT |
| libunibreak | 304585d8e2d63187507368d612c3d5fff1486368 | zlib-style |

The small modified JNI layer and rebuilding instructions are in
`third_party/nextlib_compat`. All other native dependency binaries are unmodified.
The matching Release's `third-party-sources-0.1.11.zip` preserves the native
sources, license notices and build scripts, including libass-cmake's exact
submodule revisions. The app's corresponding source is the matching Git tag.

This software uses the FreeType Project (https://freetype.org).
The generated test media contains synthetic FFmpeg test patterns and sine waves;
it contains no user media.
