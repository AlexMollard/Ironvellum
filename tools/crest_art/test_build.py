"""Regression checks for styles used by the approved Aurora and Void drawings."""
import unittest
import xml.etree.ElementTree as ET
import build


class CrestStylesTest(unittest.TestCase):
    def shapes(self, fragment, name="aurora"):
        root = ET.fromstring('<g xmlns="http://www.w3.org/2000/svg">' + fragment + '</g>')
        result = []
        build.walk(root, {}, [], result, name)
        return result

    def test_translucent_fill_has_no_unintended_outline(self):
        shape = self.shapes('<path d="M0 0H10V10Z" fill-opacity="1" opacity=".18" stroke="none"/>')[0]
        self.assertEqual(.18, shape[1])
        self.assertEqual(0, shape[2])

    def test_group_and_path_opacity_multiply(self):
        shape = self.shapes('<g opacity=".5"><path d="M0 0H10" fill="none" opacity=".4"/></g>')[0]
        self.assertAlmostEqual(.2, shape[2])

    def test_void_dark_centre_is_not_a_metal_fill(self):
        shape = self.shapes('<circle cx="50" cy="50" r="28" fill="#0B0B0D" stroke="none"/>', "void")[0]
        self.assertEqual((0, 0), shape[1:3])
        self.assertTrue(shape[-1])

    def test_opaque_metal_fill_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.shapes('<path d="M0 0H10V10Z"/>')


if __name__ == "__main__":
    unittest.main()
