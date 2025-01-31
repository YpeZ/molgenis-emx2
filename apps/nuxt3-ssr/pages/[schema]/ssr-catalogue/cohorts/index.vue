<script setup lang="ts">
const route = useRoute();
const router = useRouter();
const config = useRuntimeConfig();
const pageSize = 10;

useHead({ title: "Cohorts" });

const currentPage = ref(1);
if (route.query?.page) {
  const queryPageNumber = Number(route.query?.page);
  currentPage.value =
    typeof queryPageNumber === "number" ? Math.round(queryPageNumber) : 1;
}
let offset = computed(() => (currentPage.value - 1) * pageSize);

let filters = reactive([
  {
    title: "Search in cohorts",
    columnType: "_SEARCH",
    search: "",
    searchTables: ["collectionEvents", "subcohorts"],
    initialCollapsed: false,
  },
  {
    title: "Areas of information",
    refTable: "AreasOfInformation",
    columnName: "areasOfInformation",
    columnType: "ONTOLOGY",
    filterTable: "collectionEvents",
    conditions: [],
  },
  {
    title: "Data categories",
    refTable: "DataCategories",
    columnName: "dataCategories",
    columnType: "ONTOLOGY",
    filterTable: "collectionEvents",
    conditions: [],
  },
  {
    title: "Population age groups",
    refTable: "AgeGroups",
    columnName: "ageGroups",
    columnType: "ONTOLOGY",
    filterTable: "collectionEvents",
    conditions: [],
  },
  {
    title: "Sample categories",
    refTable: "SampleCategories",
    columnName: "sampleCategories",
    columnType: "ONTOLOGY",
    filterTable: "collectionEvents",
    conditions: [],
  },
]);

let search = computed(() => {
  // @ts-ignore
  return filters.find((f) => f.columnType === "_SEARCH").search;
});

const query = computed(() => {
  return `
  query Cohorts($filter:CohortsFilter, $orderby:Cohortsorderby){
    Cohorts(limit: ${pageSize} offset: ${offset.value} search:"${search.value}" filter:$filter  orderby:$orderby) {
      pid
      name
      acronym
      description
      keywords
      numberOfParticipants
      startYear
      endYear
      type {
          name
      }
      design {
          name
      }
      institution {
          name
          acronym
      }
    }
    Cohorts_agg (filter:$filter, search:"${search.value}"){
        count
    }
  }
  `;
});

const orderby = { acronym: "ASC" };

function buildFilterVariables() {
  const filtersVariables = filters.reduce<
    Record<string, Record<string, object | string>>
  >((accum, filter) => {
    if (filter.filterTable && filter?.conditions?.length) {
      if (!accum[filter.filterTable]) {
        accum[filter.filterTable] = {};
      }
      accum[filter.filterTable][filter.columnName] = {
        equals: filter.conditions,
      };
    }

    return accum;
  }, {});

  return filtersVariables;
}

const filter = computed(() => {
  // build the active filters
  const filterVariables = buildFilterVariables();

  // append search to the sub tables if set
  const searchTables = filters.find((f) => f.columnType === "_SEARCH")
    ?.searchTables;

  if (searchTables) {
    searchTables.forEach((searchTable) => {
      if (search.value) {
        if (Object.keys(filterVariables).includes(searchTable)) {
          filterVariables[searchTable]["_search"] = search.value;
        } else {
          filterVariables[searchTable] = { _search: search.value };
        }
      }
    });
  }

  return filterVariables;
});

let graphqlURL = computed(() => `/${route.params.schema}/catalogue/graphql`);
const { data, pending, error, refresh } = await useFetch(graphqlURL.value, {
  key: `cohorts-${offset.value}`,
  baseURL: config.public.apiBase,
  method: "POST",
  body: {
    query,
    variables: { orderby, filter },
  },
});

function setCurrentPage(pageNumber: number) {
  router.push({ path: route.path, query: { page: pageNumber } });
  currentPage.value = pageNumber;
}

watch(filters, () => {
  setCurrentPage(1);
});

let activeName = ref("detailed");

const NOTICE_SETTING_KEY = "CATALOGUE_NOTICE";
const underConstructionNotice = ref();

fetchSetting(NOTICE_SETTING_KEY).then((resp) => {
  const setting = resp.data["_settings"].find(
    (setting: { key: string; value: string }) => {
      return setting.key === NOTICE_SETTING_KEY;
    }
  );

  if (setting) {
    underConstructionNotice.value = setting.value;
  }
});
</script>

<template>
  <LayoutsSearchPage>
    <template #side>
      <SearchFilter title="Filters" :filters="filters" />
    </template>
    <template #main>
      <SearchResults>
        <template #header>
          <!-- <NavigationIconsMobile :link="" /> -->
          <PageHeader
            title="Cohorts"
            description="Group of individuals sharing a defining demographic characteristic."
            icon="image-link"
          >
            <template #suffix>
              <div
                v-if="underConstructionNotice"
                class="mt-1 mb-5 text-left bg-yellow-200 rounded-lg text-black py-5 px-5 flex"
              >
                <BaseIcon
                  name="info"
                  :width="55"
                  class="hidden md:block mr-3"
                />
                <div class="inline-block">{{ underConstructionNotice }}</div>
              </div>

              <SearchResultsViewTabs
                class="hidden xl:flex"
                buttonLeftLabel="Detailed"
                buttonLeftName="detailed"
                buttonLeftIcon="view-normal"
                buttonRightLabel="Compact"
                buttonRightName="compact"
                buttonRightIcon="view-compact"
                v-model:activeName="activeName"
              />
              <SearchResultsViewTabsMobile
                class="flex xl:hidden"
                v-model:activeName="activeName"
              >
                <SearchFilter title="Filters" :filters="filters" />
              </SearchResultsViewTabsMobile>
            </template>
          </PageHeader>
        </template>

        <template #search-results>
          <FilterWell :filters="filters"></FilterWell>
          <SearchResultsList>
            <CardList v-if="data?.data?.Cohorts?.length > 0">
              <CardListItem
                v-for="cohort in data?.data?.Cohorts"
                :key="cohort.name"
              >
                <CohortCard
                  :cohort="cohort"
                  :schema="route.params.schema"
                  :compact="activeName !== 'detailed'"
                />
              </CardListItem>
            </CardList>
            <div v-else class="flex justify-center pt-3">
              <span class="py-15 text-blue-500">
                No Cohorts found with current filters
              </span>
            </div>
          </SearchResultsList>
        </template>

        <template v-if="data?.data?.Cohorts?.length > 0" #pagination>
          <Pagination
            :current-page="currentPage"
            :totalPages="Math.ceil(data?.data?.Cohorts_agg.count / pageSize)"
            @update="setCurrentPage($event)"
          />
        </template>
      </SearchResults>
    </template>
  </LayoutsSearchPage>
</template>
