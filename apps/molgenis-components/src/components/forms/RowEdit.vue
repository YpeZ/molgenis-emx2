<template>
  <div>
    <FormInput
      v-for="column in columnsWithoutMeta.filter(showColumn)"
      :key="JSON.stringify(column)"
      :id="`${id}-${column.name}`"
      v-model="internalValues[column.id]"
      :columnType="column.columnType"
      :description="getColumnDescription(column)"
      :errorMessage="errorPerColumn[column.id]"
      :label="getColumnLabel(column)"
      :schemaName="column.refSchema ? column.refSchema : schemaMetaData.name"
      :pkey="getPrimaryKey(internalValues, tableMetaData)"
      :readonly="
        column.readonly ||
        (pkey && column.key === 1 && !clone) ||
        (column.computed !== undefined && column.computed.trim() != '')
      "
      :refBack="column.refBack"
      :refTablePrimaryKeyObject="getPrimaryKey(internalValues, tableMetaData)"
      :refLabel="column.refLabel ? column.refLabel : column.refLabelDefault"
      :required="column.required"
      :tableName="column.refTable"
      :canEdit="canEdit"
      :filter="refLinkFilter(column)"
    />
  </div>
</template>

<script>
import FormInput from "./FormInput.vue";
import constants from "../constants.js";
import {
  getPrimaryKey,
  deepClone,
  convertToCamelCase,
  getLocalizedLabel,
  getLocalizedDescription,
} from "../utils";
const { EMAIL_REGEX, HYPERLINK_REGEX } = constants;

export default {
  name: "RowEdit",
  data: function () {
    return {
      internalValues: deepClone(this.modelValue),
      errorPerColumn: {},
    };
  },
  props: {
    modelValue: {
      type: Object,
      required: true,
    },
    // id: used as html id for components
    id: {
      type: String,
      required: true,
    },
    // tableName: name of the molgenis table loaded
    tableName: {
      type: String,
      required: true,
    },
    // defines the form structure
    tableMetaData: {
      type: Object,
      required: true,
    },
    // pkey:  when updating existing record, this is the primary key value
    pkey: { type: Object },
    // clone: when you want to clone instead of update
    clone: {
      type: Boolean,
      required: false,
    },
    // visibleColumns:  visible columns, useful if you only want to allow partial edit (column of object)
    visibleColumns: {
      type: Array,
      required: false,
    },
    // defaultValue: when creating new record, this is initialization value
    defaultValue: {
      type: Object,
      required: false,
    },
    // object with the whole schema, needed to create refLink filter
    schemaMetaData: {
      type: Object,
      required: true,
    },
    canEdit: {
      type: Boolean,
      required: false,
      default: () => true,
    },
    locale: {
      type: String,
      default: () => "en",
    },
  },
  emits: ["update:modelValue"],
  components: {
    FormInput,
  },
  computed: {
    columnsWithoutMeta() {
      return this?.tableMetaData?.columns
        ? this.tableMetaData.columns.filter(
            (column) => !column.name?.startsWith("mg_")
          )
        : [];
    },
    graphqlFilter() {
      if (this.tableMetaData && this.pkey) {
        return this.tableMetaData.columns
          .filter((column) => column.key == 1)
          .reduce((accum, column) => {
            accum[column.id] = { equals: this.pkey[column.id] };
            return accum;
          }, {});
      } else {
        return {};
      }
    },
  },
  methods: {
    getPrimaryKey,
    getColumnLabel(column) {
      return getLocalizedLabel(column, this.locale);
    },
    getColumnDescription(column) {
      return getLocalizedDescription(column, this.locale);
    },
    showColumn(column) {
      if (column.reflink) {
        return this.internalValues[convertToCamelCase(column.refLink)];
      } else {
        const isColumnVisible = Array.isArray(this.visibleColumns)
          ? this.visibleColumns.find((col) => col.name === column.name)
          : true;
        return (
          isColumnVisible &&
          this.visible(column.visible, column.id) &&
          column.name !== "mg_tableclass"
        );
      }
    },
    visible(expression, columnId) {
      if (expression) {
        try {
          return executeExpression(
            expression,
            this.internalValues,
            this.tableMetaData
          );
        } catch (error) {
          this.errorPerColumn[
            columnId
          ] = `Invalid visibility expression, reason: ${error}`;
          return true;
        }
      } else {
        return true;
      }
    },
    validateTable() {
      this.tableMetaData?.columns
        ?.filter((column) => {
          if (this.visibleColumns) {
            return this.visibleColumns.find(
              (visibleColumn) => column.name === visibleColumn.name
            );
          } else {
            return true;
          }
        })
        .forEach((column) => {
          this.errorPerColumn[column.id] = getColumnError(
            column,
            this.internalValues,
            this.tableMetaData
          );
        });
    },
    applyComputed() {
      //apply computed
      this.tableMetaData.columns.forEach((c) => {
        if (c.computed) {
          try {
            this.internalValues[c.id] = executeExpression(
              c.computed,
              this.internalValues,
              this.tableMetaData
            );
          } catch (error) {
            this.errorPerColumn[c.id] = "Computation failed: " + error;
          }
        }
      });
    },
    //create a filter in case inputs are linked by overlapping refs
    refLinkFilter(c) {
      //need to figure out what refs overlap
      if (
        c.refLink &&
        this.showColumn(c) &&
        this.internalValues[convertToCamelCase(c.refLink)]
      ) {
        let filter = {};
        this.tableMetaData.columns.forEach((c2) => {
          if (c2.name === c.refLink) {
            this.schemaMetaData.tables.forEach((t) => {
              //check how the reftable overlaps with columns in our column
              if (t.name === c.refTable) {
                t.columns.forEach((c3) => {
                  if (c3.key === 1 && c3.refTable === c2.refTable) {
                    filter[c3.name] = {
                      equals:
                        this.internalValues[convertToCamelCase(c.refLink)],
                    };
                  }
                });
              }
            });
          }
        });
        return filter;
      }
    },
  },
  watch: {
    internalValues: {
      handler() {
        //clean up errors
        this.errorPerColumn = {};
        this.validateTable();
        this.applyComputed();
        this.$emit("update:modelValue", this.internalValues);
      },
      deep: true,
    },
    tableMetaData: {
      handler() {
        this.validateTable();
        this.applyComputed();
      },
      deep: true,
    },
  },
  created() {
    if (this.defaultValue) {
      this.internalValues = deepClone(this.defaultValue);
    }
    this.validateTable();
    this.applyComputed();
  },
};

function getColumnError(column, values, tableMetaData) {
  const value = values[column.id];
  const isInvalidNumber = typeof value === "number" && isNaN(value);
  const missesValue = value === undefined || value === null || value === "";
  const type = column.columnType;

  if (column.required && (missesValue || isInvalidNumber)) {
    return column.name + " is required ";
  }
  if (missesValue) {
    return undefined;
  }
  if (type === "EMAIL" && !isValidEmail(value)) {
    return "Invalid email address";
  }
  if (type === "EMAIL_ARRAY" && containsInvalidEmail(value)) {
    return "Invalid email address";
  }
  if (type === "HYPERLINK" && !isValidHyperlink(value)) {
    return "Invalid hyperlink";
  }
  if (type === "HYPERLINK_ARRAY" && containsInvalidHyperlink(value)) {
    return "Invalid hyperlink";
  }
  if (column.validation) {
    return getColumnValidationError(column, values, tableMetaData);
  }
  if (isRefLinkWithoutOverlap(column, tableMetaData, values)) {
    return `value should match your selection in column '${column.refLink}' `;
  }

  return undefined;
}

function getColumnValidationError(column, values, tableMetaData) {
  try {
    //use the keys as variables
    const result = executeExpression(column.validation, values, tableMetaData);
    if (result === false) {
      return `Applying validation rule returned error: ${column.validation}`;
    } else if (result === true || result === undefined) {
      return undefined;
    } else {
      return `Applying validation rule returned error: ${result}`;
    }
  } catch (error) {
    return `Invalid validation expression '${column.validation}', reason: ${error}`;
  }
}

function executeExpression(expression, values, tableMetaData) {
  //make sure all columns have keys to prevent reference errors
  const copy = deepClone(values);
  tableMetaData.columns.forEach((c) => {
    if (!copy.hasOwnProperty(c.id)) {
      copy[c.id] = null;
    }
  });

  const func = new Function(
    Object.keys(copy),
    `return eval('${expression.replaceAll("'", '"')}');`
  );
  return func(...Object.values(copy));
}

function isRefLinkWithoutOverlap(column, tableMetaData, values) {
  if (!column.refLink) {
    return false;
  }
  const columnRefLink = column.refLink;
  const refLinkId = convertToCamelCase(columnRefLink);

  const value = values[column.id];
  const refValue = values[refLinkId];

  if (typeof value === "string" && typeof refValue === "string") {
    return value && refValue && value !== refValue;
  } else {
    return (
      value &&
      refValue &&
      !JSON.stringify(value).includes(JSON.stringify(refValue))
    );
  }
}

function isValidHyperlink(value) {
  return HYPERLINK_REGEX.test(String(value).toLowerCase());
}

function containsInvalidHyperlink(hyperlinks) {
  return hyperlinks.find((hyperlink) => !this.isValidHyperlink(hyperlink));
}

function isValidEmail(value) {
  return EMAIL_REGEX.test(String(value).toLowerCase());
}

function containsInvalidEmail(emails) {
  return emails.find((email) => !this.isValidEmail(email));
}
</script>

<docs>
<template>
  <DemoItem>
    <div class="row">
      <div class="col-6">
        <label class="border-bottom">In create mode</label>
        <RowEdit
            v-if="showRowEdit"
            id="row-edit"
            v-model="rowData"
            :tableName="tableName"
            :tableMetaData="tableMetaData"
            :locale="locale"
            :schemaMetaData="schemaMetaData"
        />
      </div>
      <div class="col-6 border-left">
        <label for="create-mode-config" class="border-bottom">Meta data</label>
        <dl id="create-mode-config">
          <dt>Table name</dt>
          <dd>
            <select v-model="tableName">
              <option>Pet</option>
              <option>Order</option>
              <option>Category</option>
              <option>User</option>
            </select>
          </dd>
          <InputString v-model="locale" label="locale" id="locale"/>
          <dt>Row data</dt>
          <dd>{{ rowData }}</dd>

          <dt>MetaData</dt>
          <dd>{{ tableMetaData }}</dd>
        </dl>
      </div>
    </div>
  </DemoItem>
</template>
<script>
  export default {
    data: function() {
      return {
        showRowEdit: true,
        locale: 'en',
        tableName: 'Pet',
        tableMetaData: {
          columns: [],
        },
        schemaMetaData: {},
        rowData: {},
        schemaName: 'pet store',
      };
    },
    watch: {
      async tableName(newValue, oldValue) {
        if (newValue !== oldValue) {
          this.rowData = {};
          await this.reload();
        }
      },
      async locale(newValue, oldValue) {
        if (newValue !== oldValue) {
          this.rowData = {};
          await this.reload();
        }
      },
    },
    methods: {
      async reload() {
        // force complete component reload to have a clean demo component and hit all lifecycle events
        this.showRowEdit = false;
        const client = this.$Client.newClient(this.schemaName);
        this.schemaMetaData = await client.fetchSchemaMetaData();
        this.tableMetaData = await client.fetchTableMetaData(this.tableName);
        // this.rowData = (await client.fetchTableData(this.tableName))[this.tableName];
        this.showRowEdit = true;
      },
    },
    async mounted() {
      this.reload();
    },
  };
</script>
</docs>
