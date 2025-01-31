<template>
  <FormGroup
    :id="id"
    :label="label"
    :required="required"
    :description="description"
    :errorMessage="errorMessage"
  >
    <div>
      <div>
        <div v-if="count > maxNum">
          <FilterWell
            v-for="(item, key) in selection"
            :key="JSON.stringify(item)"
            :label="flattenObject(item)"
            @click="deselect(key)"
          />
        </div>
        <ButtonAlt
          v-if="modelValue && modelValue.length"
          class="pl-1"
          @click="clearValue"
        >
          clear selection
        </ButtonAlt>
      </div>
      <div
        :class="
          showMultipleColumns ? 'd-flex align-content-stretch flex-wrap' : ''
        "
      >
        <div
          class="form-check custom-control custom-checkbox"
          :class="showMultipleColumns ? 'col-12 col-md-6 col-lg-4' : ''"
          v-for="(row, index) in data"
          :key="index"
        >
          <input
            :id="`${id}-${row.name}`"
            :name="id"
            type="checkbox"
            :value="getPrimaryKey(row, tableMetaData)"
            v-model="selection"
            @change="emitSelection"
            class="form-check-input"
            :class="{ 'is-invalid': errorMessage }"
          />
          <label class="form-check-label" :for="`${id}-${row.name}`">
            {{ flattenObject(getPrimaryKey(row, tableMetaData)) }}
          </label>
        </div>
        <ButtonAlt
          class="pl-0"
          :class="showMultipleColumns ? 'col-12 col-md-6 col-lg-4' : ''"
          icon="fa fa-search"
          @click="openSelect"
        >
          {{ count > maxNum ? `view all ${count} options.` : "view as table" }}
        </ButtonAlt>
      </div>
      <LayoutModal v-if="showSelect" :title="title" @close="closeSelect">
        <template v-slot:body>
          <TableSearch
            v-model:selection="selection"
            @update:selection="$emit('update:modelValue', $event)"
            :lookupTableName="tableName"
            :filter="filter"
            @select="emitSelection"
            @deselect="deselect"
            :schemaName="schemaName"
            :showSelect="true"
            :limit="10"
            :canEdit="canEdit"
          />
        </template>
        <template v-slot:footer>
          <ButtonAlt @click="closeSelect">Close</ButtonAlt>
        </template>
      </LayoutModal>
    </div>
  </FormGroup>
</template>

<script>
import Client from "../../client/client.ts";
import BaseInput from "./baseInputs/BaseInput.vue";
import TableSearch from "../tables/TableSearch.vue";
import LayoutModal from "../layout/LayoutModal.vue";
import FormGroup from "./FormGroup.vue";
import ButtonAlt from "./ButtonAlt.vue";
import FilterWell from "../filters/FilterWell.vue";
import { convertToPascalCase, flattenObject, getPrimaryKey } from "../utils";

export default {
  extends: BaseInput,
  data: function () {
    return {
      client: null,
      showSelect: false,
      data: [],
      selection: this.modelValue,
      count: 0,
      tableMetaData: null,
    };
  },
  components: {
    FilterWell,
    TableSearch,
    LayoutModal,
    FormGroup,
    ButtonAlt,
  },
  props: {
    schemaName: {
      type: String,
      required: false,
    },
    filter: Object,
    multipleColumns: Boolean,
    maxNum: { type: Number, default: 11 },
    tableName: {
      type: String,
      required: true,
    },
    /**
     * Whether or not the buttons are show to edit the referenced table
     *  */
    canEdit: {
      type: Boolean,
      required: false,
      default: () => false,
    },
  },
  computed: {
    tableId() {
      return convertToPascalCase(this.tableName);
    },
    title() {
      return "Select " + this.tableName;
    },
    showMultipleColumns() {
      const itemsPerColumn = 12;
      return this.multipleColumns && this.count > itemsPerColumn;
    },
  },
  methods: {
    getPrimaryKey,
    deselect(key) {
      this.selection.splice(key, 1);
      this.emitSelection();
    },
    clearValue() {
      this.selection = [];
      this.emitSelection();
    },
    emitSelection() {
      this.$emit("update:modelValue", this.selection);
    },
    openSelect() {
      this.showSelect = true;
    },
    closeSelect() {
      this.loadOptions();
      this.showSelect = false;
    },
    flattenObject,
    async loadOptions() {
      const options = {
        limit: this.maxNum,
      };
      if (this.filter) {
        options["filter"] = this.filter;
      }
      const response = await this.client.fetchTableData(this.tableId, options);
      this.data = response[this.tableId];
      this.count = response[this.tableId + "_agg"].count;
    },
  },
  watch: {
    modelValue() {
      this.selection = this.modelValue;
    },
    filter() {
      this.loadOptions();
    },
  },
  async mounted() {
    this.client = Client.newClient(this.schemaName);
    this.tableMetaData = await this.client.fetchTableMetaData(this.tableName);
    await this.loadOptions();
    if (!this.modelValue) {
      this.selection = [];
    }
  },
};
</script>

<docs>
<template>
  <div>
    You have to be have server running and be signed in for this to work
    <div class="border-bottom mb-3 p-2">
      <h5>synced demo props: </h5>
        <div>
          <label for="canEdit" class="pr-1">can edit: </label>
          <input type="checkbox" id="canEdit" v-model="canEdit">
        </div>
        <p class="font-italic">view in table mode to see edit action buttons</p>
    </div>
    <DemoItem>
      <!-- normally you don't need schemaName, it will use graphql on current path-->
      <InputRefList
        id="input-ref-list"
        label="Standard ref input list"
        v-model="value"
        tableName="Pet"
        description="Standard input"
        schemaName="pet store"
        :canEdit="canEdit"
      />
      Selection: {{ value }}
    </DemoItem>
    <DemoItem>
      <InputRefList
        id="input-ref-list-default"
        label="Ref input list with default value"
        v-model="defaultValue"
        tableName="Pet"
        description="This is a default value"
        :defaultValue="defaultValue"
        schemaName="pet store"
        :canEdit="canEdit"
      />
      Selection: {{ defaultValue }}
    </DemoItem>
    <DemoItem>
      <InputRefList
        id="input-ref-list-filter"
        label="Ref input list with pre set filter"
        v-model="filterValue"
        tableName="Pet"
        description="Filter by name"
        :filter="{ category: { name: { equals: 'pooky' } } }"
        schemaName="pet store"
        :canEdit="canEdit"
      />
      Selection: {{ filterValue }}
    </DemoItem>
    <DemoItem>
      <InputRefList
        id="input-ref-list"
        label="Ref input list with multiple columns"
        v-model="multiColumnValue"
        tableName="Pet"
        description="This is a multi column input"
        schemaName="pet store"
        multipleColumns
        :canEdit="canEdit"
      />
      Selection: {{ multiColumnValue }}
    </DemoItem>
  </div>
</template>

<script>
export default {
  data: function () {
    return {
      value: null,
      defaultValue: [{ name: "pooky" }, { name: "spike" }],
      filterValue: [{ name: "spike" }],
      multiColumnValue: null,
      canEdit: false
    };
  },
};
</script>
</docs>
