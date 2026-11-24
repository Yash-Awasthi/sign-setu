import unittest

import count_paths


class WaysOutTest(unittest.TestCase):
    def body(self, src: str) -> str:
        return src

    def test_a_labelled_button_is_one_way_out(self):
        src = 'Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wrap_save)) }'
        self.assertEqual({"wrap_save"}, count_paths.ways_out(self.body(src)))

    def test_an_armed_delete_is_still_one_path(self):
        src = """
        TextButton(onClick = { if (sure) onDelete() else sure = true }) {
            Text(stringResource(if (sure) R.string.wrap_delete_sure else R.string.wrap_delete))
        }
        """
        self.assertEqual({"wrap_delete"}, count_paths.ways_out(self.body(src)))

    def test_a_discard_is_the_same_path_worded_for_a_reopened_session(self):
        src = 'TextButton(onClick = onDelete) { Text(stringResource(R.string.wrap_discard)) }'
        self.assertEqual({"wrap_delete"}, count_paths.ways_out(self.body(src)))

    def test_keep_going_keeps_you_there_so_it_is_no_path(self):
        src = 'TextButton(onClick = onDismiss) { Text(stringResource(R.string.wrap_keep_going)) }'
        self.assertEqual(set(), count_paths.ways_out(self.body(src)))

    def test_an_ordinary_action_is_not_a_way_out(self):
        src = 'IconButton(onClick = call) { Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.officer_call)) }'
        self.assertEqual(set(), count_paths.ways_out(self.body(src)))

    def test_a_label_outside_a_control_does_not_count(self):
        src = 'Text(stringResource(R.string.wrap_save))'
        self.assertEqual(set(), count_paths.ways_out(self.body(src)))

    def test_toggle_rows_are_not_counted(self):
        src = 'Row(Modifier.toggleable(on = true, role = Role.Checkbox) {}) { Text(stringResource(R.string.end_export)) }'
        self.assertEqual(set(), count_paths.ways_out(self.body(src)))


class FunctionsTest(unittest.TestCase):
    def test_walks_top_level_functions_with_their_braces(self):
        src = """
internal fun Outer() {
    Text("a")
    Inner()
}

fun Inner() {
    Text("b")
}
"""
        names = [name for name, _ in count_paths.functions(src)]
        self.assertEqual(["Outer", "Inner"], names)

    def test_a_function_without_a_body_is_skipped(self):
        self.assertEqual([], count_paths.functions("fun abstract(): Int\n"))


if __name__ == "__main__":
    unittest.main()
