import { useState } from "react";
import { useForm } from "react-hook-form";
import { api } from "../api";
import { Modal } from "../components/Modal";
import { minute, message, numberOrNull, parseWindows } from "../lib/format";
import type { ItemInput, RecordItem, RecordMode, ValueType } from "../types";

interface ItemFormValues {
  name: string;
  description: string;
  mode: RecordMode;
  valueType: ValueType;
  defaultValue: string | number;
  minValue: string | number;
  maxValue: string | number;
  stepValue: string | number;
  unit: string;
  goalValue: string | number;
  goalPeriod: "DAY" | "DAYS_7";
  priority: string | number;
  windows: string;
}

export function ItemForm({
  item,
  onClose,
  onSaved,
}: {
  item?: RecordItem;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    watch,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<ItemFormValues>({
    defaultValues: {
      name: item?.name ?? "",
      description: item?.description ?? "",
      mode: item?.mode ?? "ACCUMULATE",
      valueType: item?.valueType ?? "NUMBER",
      defaultValue: item?.defaultValue ?? "",
      minValue: item?.minValue ?? "",
      maxValue: item?.maxValue ?? "",
      stepValue: item?.stepValue ?? 1,
      unit: item?.unit ?? "次",
      goalValue: item?.goalValue ?? "",
      goalPeriod: item?.goalPeriod ?? "DAY",
      priority: item?.priority ?? 0,
      windows:
        item?.windows.map((window) => `${minute(window.startMinute)}-${minute(window.endMinute)}`).join("\n") ?? "",
    },
  });

  const mode = watch("mode");
  const valueType = watch("valueType");
  const measurement = mode === "MEASUREMENT";

  return (
    <Modal title={item ? "Edit record item" : "New record item"} onClose={onClose} size="lg">
      <form
        className="item-form"
        onSubmit={handleSubmit(async (values) => {
          setFormError(null);
          const input: ItemInput = {
            name: values.name,
            description: values.description,
            mode: values.mode,
            valueType: measurement ? "NUMBER" : values.valueType,
            defaultValue: measurement ? null : numberOrNull(values.defaultValue),
            minValue: measurement ? Number(values.minValue) : null,
            maxValue: measurement ? Number(values.maxValue) : null,
            stepValue: Number(values.stepValue),
            unit: values.unit,
            goalValue: measurement ? null : numberOrNull(values.goalValue),
            goalPeriod: measurement || !values.goalValue ? null : values.goalPeriod,
            priority: Number(values.priority),
            windows: measurement ? [] : parseWindows(values.windows),
          };

          try {
            await api.saveItem(input, item?.id);
            await onSaved();
          } catch (reason) {
            setFormError(message(reason));
          }
        })}
      >
        <section className="form-section">
          <h3>Basics</h3>
          <div className="grid">
            <label>
              Name
              <input {...register("name", { required: true })} />
            </label>
            <label>
              Priority
              <input type="number" step="1" {...register("priority", { required: true })} />
            </label>
          </div>
          <label>
            Description
            <textarea {...register("description")} />
          </label>
          <div className="grid">
            <label>
              Mode
              <select {...register("mode")}>
                <option value="ACCUMULATE">Accumulate</option>
                <option value="MEASUREMENT">Measurement</option>
              </select>
            </label>
            {measurement ? (
              <label>
                Type
                <input value="Number" readOnly />
              </label>
            ) : (
              <label>
                Type
                <select {...register("valueType")}>
                  <option value="NUMBER">Number</option>
                  <option value="DURATION">Duration</option>
                </select>
              </label>
            )}
            <label>
              Unit
              {valueType === "DURATION" && !measurement ? (
                <select {...register("unit")}>
                  <option>秒</option>
                  <option>分</option>
                  <option>小时</option>
                </select>
              ) : (
                <input {...register("unit", { required: true })} />
              )}
            </label>
          </div>
        </section>

        <section className="form-section">
          <h3>Values</h3>
          <div className="grid">
            {measurement ? (
              <>
                <label>
                  Minimum
                  <input type="number" step="any" {...register("minValue", { required: true })} />
                </label>
                <label>
                  Maximum
                  <input type="number" step="any" {...register("maxValue", { required: true })} />
                </label>
              </>
            ) : (
              <label>
                Default value
                <input type="number" min="0" step="any" {...register("defaultValue")} />
              </label>
            )}
            <label>
              Step
              <input type="number" min="0.000001" step="any" {...register("stepValue", { required: true })} />
            </label>
            {!measurement && (
              <>
                <label>
                  Goal
                  <input type="number" min="0" step="any" {...register("goalValue")} />
                </label>
                <label>
                  Goal period
                  <select {...register("goalPeriod")}>
                    <option value="DAY">Day</option>
                    <option value="DAYS_7">7 days</option>
                  </select>
                </label>
              </>
            )}
          </div>
        </section>

        {!measurement && (
          <section className="form-section">
            <h3>Schedule</h3>
            <label>
              Suggested windows
              <textarea placeholder="08:00-09:00, one per line" {...register("windows")} />
            </label>
          </section>
        )}

        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}

        <div className="form-actions">
          <button type="button" className="secondary" onClick={onClose}>
            Cancel
          </button>
          <button disabled={isSubmitting}>{isSubmitting ? "Saving…" : "Save"}</button>
        </div>
      </form>
    </Modal>
  );
}
