<script setup>
let schema;
if (useRoute) {
  schema = useRoute().params.schema;
}
const menu = [
  { label: "Home", link: `/${schema}/ssr-catalogue/` },
  // { label: "Variables", link: "#", highlight: true },
  // { label: "Networks", link: "#" },
  // { label: "Statistical Methods", link: "#" },
  // { label: "Tables", link: "#" },
  // { label: "Manuals", link: "#" },
  { label: "Cohorts", link: `/${schema}/ssr-catalogue/cohorts` },
  { label: "About", link: `/${schema}/ssr-catalogue/about` },
];
</script>

<template>
  <header class="antialiased px-5 lg:px-0 xl:bg-navigation">
    <Container>
      <div class="items-center justify-between hidden xl:flex h-25">
        <Logo :link="`/${schema}/ssr-catalogue`" />
        <MainNavigation :navigation="menu" />
        <!--  <div class="w-[450px]">
           <SearchBar />
        </div>-->

        <!-- <HeaderButton label="Favorites" icon="star" />
        <HeaderButton label="Account" icon="user" /> -->
      </div>

      <div class="pt-5 xl:hidden">
        <div class="relative flex items-center h-12.5 justify-between mb-4">
          <!-- <HamburgerMenu :navigation="menu" /> -->

          <div class="absolute -translate-x-1/2 left-1/2">
            <LogoMobile :link="`/${schema}/ssr-catalogue`" />
          </div>

          <div class="flex gap-3">
            <!-- <HeaderButton label="Favorites" icon="star" />
            <HeaderButton label="Account" icon="user" /> -->
          </div>
        </div>

        <MainNavigation :navigation="menu" :showMoreButton="false" />
        <div class="w-full pt-6">
          <!-- <SearchBar /> -->
        </div>
      </div>
    </Container>
  </header>
</template>
