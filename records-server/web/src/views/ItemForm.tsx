import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Save } from "lucide-react";
import { api } from "../api";
import { Modal } from "../components/Modal";
import { message, numberOrNull } from "../lib/format";
import type { ItemInput, RecordItem, RecordMode, ValueType } from "../types";

interface ItemFormValues {
  name: string;
  description: string;
  valueType: ValueType;
  defaultValue: string | number;
  minValue: string | number;
  maxValue: string | number;
  stepValue: string | number;
  unit: string;
  goalValue: string | number;
  goalPeriod: "DAY" | "DAYS_7";
  priority: string | number;
}

export function ItemForm({
  item,
  mode,
  onClose,
  onSaved,
}: {
  item?: RecordItem;
  mode: RecordMode;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const measurement = mode === "MEASUREMENT";
  const { register, watch, setValue, handleSubmit, formState: { isSubmitting } } = useForm<ItemFormValues>({
    defaultValues: {
      name: item?.name ?? "",
      description: item?.description ?? "",
      valueType: item?.valueType ?? "NUMBER",
      defaultValue: item?.defaultValue ?? "",
      minValue: item?.minValue ?? "",
      maxValue: item?.maxValue ?? "",
      stepValue: item?.stepValue ?? 1,
      unit: item?.unit ?? (measurement ? "" : "次"),
      goalValue: item?.goalValue ?? "",
      goalPeriod: item?.goalPeriod ?? "DAY",
      priority: item?.priority ?? 0,
    },
  });
  const valueType = watch("valueType");
  const unit = watch("unit");

  useEffect(() => {
    if (!measurement && valueType === "DURATION" && !["秒", "分", "小时"].includes(unit)) {
      setValue("unit", "秒");
    }
  }, [measurement, setValue, unit, valueType]);

  return (
    <Modal title={`${item ? "Edit" : "New"} ${measurement ? "measurement" : "accumulate"}`} onClose={onClose} size="lg">
      <form
        className="item-form"
        onSubmit={handleSubmit(async (values) => {
          setFormError(null);
          const input: ItemInput = {
            name: values.name,
            description: values.description,
            mode,
            valueType: measurement ? "NUMBER" : values.valueType,
            defaultValue: measurement ? null : numberOrNull(values.defaultValue),
            minValue: measurement ? Number(values.minValue) : null,
            maxValue: measurement ? Number(values.maxValue) : null,
            stepValue: Number(values.stepValue),
            unit: values.unit,
            goalValue: measurement ? null : numberOrNull(values.goalValue),
            goalPeriod: measurement || !values.goalValue ? null : values.goalPeriod,
            priority: Number(values.priority),
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
          <div className="section-heading">
            <div>
              <p className="eyebrow">Configuration</p>
              <h3>{measurement ? "Measurement definition" : "Accumulation definition"}</h3>
            </div>
            <span className="type-mark">{measurement ? "MEASUREMENT" : "ACCUMULATE"}</span>
          </div>
          <div className="form-grid form-grid-2">
            <label>
              Name
              <input autoFocus {...register("name", { required: "Name is required" })} />
            </label>
            <label>
              Priority
              <input type="number" step="1" {...register("priority", { required: true })} />
            </label>
          </div>
          <label>
            Description
            <textarea {...register("description")} placeholder="Optional context for this record item" />
          </label>
          <div className="form-grid form-grid-2">
            {measurement ? (
              <label>
                Value type
                <input value="Number" readOnly />
              </label>
            ) : (
              <label>
                Value type
                <select {...register("valueType")}>
                  <option value="NUMBER">Number</option>
                  <option value="DURATION">Duration</option>
                </select>
              </label>
            )}
            <label>
              Unit
              {!measurement && valueType === "DURATION" ? (
                <select {...register("unit", { required: true })}>
                  <option value="秒">秒</option>
                  <option value="分">分</option>
                  <option value="小时">小时</option>
                </select>
              ) : (
                <input {...register("unit", { required: true })} />
              )}
            </label>
          </div>
        </section>

        <section className="form-section">
          <p className="eyebrow">Rules</p>
          <div className="form-grid form-grid-3">
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
                <input type="number" min="0.000001" step="any" {...register("defaultValue")} />
              </label>
            )}
            <label>
              Step
              <input type="number" min="0.000001" step="any" {...register("stepValue", { required: true })} />
            </label>
          </div>
          {!measurement && (
            <div className="form-grid form-grid-2">
              <label>
                Goal
                <input type="number" min="0.000001" step="any" {...register("goalValue")} />
              </label>
              <label>
                Goal period
                <select {...register("goalPeriod")}>
                  <option value="DAY">Per day</option>
                  <option value="DAYS_7">Rolling 7 days</option>
                </select>
              </label>
            </div>
          )}
        </section>

        {formError && <p className="form-error" role="alert">{formError}</p>}
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={onClose} disabled={isSubmitting}>Cancel</button>
          <button disabled={isSubmitting}><Save size={16} />{isSubmitting ? "Saving…" : "Save changes"}</button>
        </div>
      </form>
    </Modal>
  );
}
