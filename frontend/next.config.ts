import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Produces a self-contained server bundle for the container image.
  output: "standalone",
  poweredByHeader: false,
  eslint: {
    // Linting is a separate CI step; do not silently skip it here.
    ignoreDuringBuilds: false,
  },
  typescript: {
    ignoreBuildErrors: false,
  },
};

export default nextConfig;
