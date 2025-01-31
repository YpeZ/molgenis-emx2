<script setup lang="ts">
const route = useRoute();
const config = useRuntimeConfig();

const { data, pending, error, refresh } = await useFetch(
  `/${route.params.schema}/catalogue/graphql`,
  {
    baseURL: config.public.apiBase,
    method: "POST",
    body: {
      query: `{
        Cohorts_agg { 
          count
          sum {
            numberOfParticipants
            numberOfParticipantsWithSamples 
          }
        }
        Cohorts_groupBy {
          count 
          design {
            name
          }
        }
        _settings (keys: ["NOTICE_SETTING_KEY" "CATALOGUE_LANDING_TITLE" "CATALOGUE_LANDING_DESCRIPTION"]){ 
          key
          value 
        }
      }`,
    },
  }
);

function percentageLongitudinal(
  cohortsGroupBy: { count: number; design: { name: string } }[],
  total: number
) {
  const nLongitudinal = cohortsGroupBy.reduce(
    (accum, group) =>
      group?.design?.name === "Longitudinal" ? accum + group.count : accum,
    0
  );

  return Math.round((nLongitudinal / total) * 100);
}

function getSettingValue(settingKey: string, settings: ISetting[]) {
  return settings.find((setting: { key: string; value: string }) => {
    return setting.key === settingKey;
  })?.value;
}
</script>
<template>
  <LayoutsLandingPage class="w-10/12 pt-8">
    <PageHeader
      class="mx-auto lg:w-7/12"
      :title="
        getSettingValue('CATALOGUE_LANDING_TITLE', data.data._settings) ||
        'European Networks Health Data & Cohort Catalogue.'
      "
      :description="
        getSettingValue('CATALOGUE_LANDING_DESCRIPTION', data.data._settings) ||
        'Browse and manage metadata for data resources, such as cohorts, registries, biobanks, and multi-center collaborations thereof such as networks, common data models and studies.'
      "
    ></PageHeader>

    <div
      class="bg-white shadow-primary justify-around flex flex-row px-5 pt-5 pb-6 antialiased lg:pb-10 lg:px-0"
    >
      <LandingCardPrimary
        title="Cohorts"
        description="A complete overview of all cohorts and biobanks within the UMCG."
        :count="data.data.Cohorts_agg.count"
        :link="`/${route.params.schema}/ssr-catalogue/cohorts/`"
      />
    </div>

    <div
      class="justify-around flex flex-col md:flex-row pt-5 pb-5 lg:pb-10 lg:px-0"
    >
      <LandingCardSecondary icon="people">
        <b> {{ data.data.Cohorts_agg.sum.numberOfParticipants }} Participants</b
        ><br />The cumulative number of participants of all datasets combined.
      </LandingCardSecondary>

      <LandingCardSecondary icon="colorize">
        <b
          >{{
            data.data.Cohorts_agg.sum.numberOfParticipantsWithSamples
          }}
          Samples</b
        >
        <br />The cumulative number of participants with samples collected of
        all datasets combined.
      </LandingCardSecondary>

      <LandingCardSecondary icon="schedule">
        <b
          >Longitudinal
          {{
            percentageLongitudinal(
              data.data.Cohorts_groupBy,
              data.data.Cohorts_agg.count
            )
          }}%</b
        ><br />Percentage of longitudinal datasets. The remaining datasets are
        cross-sectional.
      </LandingCardSecondary>
    </div>
  </LayoutsLandingPage>
</template>
